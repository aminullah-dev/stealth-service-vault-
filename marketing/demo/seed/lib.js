"use strict";
/**
 * Shared helpers for seeding the SafeBeauty DEMO world into safebeauty-staging.
 *
 * HARD RULE: PROJECT is pinned to safebeauty-staging and asserted on every
 * request. Nothing here can address the production project.
 */

const REPO = "/Users/aminullahhashemi/Safe beauty";
const PROJECT = "safebeauty-staging";
const FORBIDDEN = "safebeauty";

const { normalizeDistrict, cityOf, AREAS } = require(`${REPO}/functions/lib/areas`);
const { categoriesFor, normalize: categoryNormalize } = require(`${REPO}/functions/lib/categories`);
const { defaultWorkingHours } = require(`${REPO}/functions/lib/hours`);

const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;

function assertStaging(url) {
  if (!url.includes(`/projects/${PROJECT}/`) && !url.includes(`/projects/${PROJECT}?`)) {
    throw new Error(`REFUSING: url does not target ${PROJECT}: ${url}`);
  }
  // Belt and braces: the prod project id is a strict prefix of the staging one,
  // so a plain includes() check is not enough on its own.
  if (new RegExp(`/projects/${FORBIDDEN}(?![-\\w])`).test(url)) {
    throw new Error(`REFUSING: url targets PRODUCTION: ${url}`);
  }
}

let TOKEN = process.env.GCLOUD_TOKEN;
function token() {
  if (!TOKEN) throw new Error("GCLOUD_TOKEN not set");
  return TOKEN;
}

async function api(method, url, body) {
  assertStaging(url);
  const res = await fetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${token()}`,
      "x-goog-user-project": PROJECT,
      "Content-Type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${url}\n  ${res.status} ${text.slice(0, 600)}`);
  return text ? JSON.parse(text) : {};
}

// ── JS value -> Firestore REST Value ─────────────────────────────────────────
// Doubles that happen to be whole numbers must stay doubles (rating 5.0 must
// not land as an int, or Kotlin's Double mapper sees a Long and the object
// fails to deserialize).
function asDouble(n) { return { __double: Number(n) }; }

// rebind so nested objects/arrays use the double-aware encoder
function toFieldsD(obj) {
  const out = {};
  for (const [k, v] of Object.entries(obj)) out[k] = encode(v);
  return out;
}
function encode(v) {
  if (v && typeof v === "object" && !Array.isArray(v) && "__double" in v) {
    return { doubleValue: v.__double };
  }
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") {
    return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  }
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encode) } };
  if (typeof v === "object") return { mapValue: { fields: toFieldsD(v) } };
  throw new Error(`unsupported value: ${typeof v}`);
}

async function setDoc(collection, docId, data) {
  const url = `${BASE}/${collection}?documentId=${encodeURIComponent(docId)}`;
  try {
    return await api("POST", url, { fields: toFieldsD(data) });
  } catch (e) {
    if (String(e.message).includes("ALREADY_EXISTS")) {
      // Full overwrite: no updateMask means "replace the document".
      return await api("PATCH", `${BASE}/${collection}/${encodeURIComponent(docId)}`,
        { fields: toFieldsD(data) });
    }
    throw e;
  }
}

async function getDoc(path) {
  try { return await api("GET", `${BASE}/${path}`); } catch { return null; }
}

async function listDocs(collection, pageSize = 100) {
  const r = await api("GET", `${BASE}/${collection}?pageSize=${pageSize}`);
  return r.documents || [];
}

async function deleteDoc(path) {
  return api("DELETE", `${BASE}/${path}`);
}

// ── The derivation, lifted verbatim from functions/domains/discovery.js ───────
// Copied rather than imported because discovery.js boots firebase-admin. The
// values it produces are checked against the deployed deriveSalonFields trigger
// after the write: if the trigger rewrites anything, these were wrong.
const NO_PRICE = 9999999;
const AREA_BY_KEY = new Map(AREAS.map((a) => [a.key, a]));

function salonMinPrice(salon) {
  const prices = (salon && salon.pricePerService) || {};
  const values = Object.values(prices).map(Number).filter((v) => Number.isFinite(v) && v > 0);
  return values.length ? Math.min(...values) : NO_PRICE;
}

function cityOfCandidates(districtKey, candidates) {
  if (districtKey) return cityOf(districtKey);
  const cities = new Set((Array.isArray(candidates) ? candidates : []).map(cityOf).filter(Boolean));
  return cities.size === 1 ? [...cities][0] : "";
}

function deriveSalonDiscovery(salon) {
  const { categories, unmatched } = categoriesFor(salon && salon.services);
  const area = normalizeDistrict(salon && salon.district);
  const districtKey = area.key || "";

  const claimed = String((salon && salon.areaKey) || "").trim();
  const finer = claimed && AREA_BY_KEY.get(claimed);
  const sameCity = finer && cityOf(finer.key) === cityOf(districtKey) && cityOf(districtKey) !== "";
  const areaKey = (finer && finer.kind !== "DISTRICT" &&
                   (finer.parent === districtKey || (!finer.parent && sameCity))) ? claimed : "";

  return {
    categories,
    districtKey,
    areaKey,
    city: cityOfCandidates(districtKey, area.candidates),
    districtRaw: String((salon && salon.district) || "").trim(),
    nameKey: categoryNormalize(salon && salon.salonName),
    minPrice: salonMinPrice(salon),
    sortRating: Number((salon && salon.rating) || 0),
    unmatchedServices: unmatched,
    districtCandidates: area.candidates || [],
  };
}

function storedDiscoveryFields(derived) {
  const review = {
    unmatchedServices: derived.unmatchedServices,
    districtCandidates: derived.districtCandidates,
  };
  return {
    categories: derived.categories,
    districtKey: derived.districtKey,
    areaKey: derived.areaKey,
    city: derived.city,
    nameKey: derived.nameKey,
    minPrice: derived.minPrice,
    sortRating: asDouble(derived.sortRating),
    needsDiscoveryReview: review.unmatchedServices.length > 0 ||
                          review.districtCandidates.length > 0 ||
                          (derived.districtRaw.length > 0 && derived.districtKey === ""),
    discoveryReview: review,
  };
}

module.exports = {
  PROJECT, BASE, REPO,
  api, setDoc, getDoc, listDocs, deleteDoc, encode, toFieldsD,
  asDouble,
  deriveSalonDiscovery, storedDiscoveryFields, defaultWorkingHours,
  categoriesFor, normalizeDistrict, categoryNormalize, cityOf,
};
