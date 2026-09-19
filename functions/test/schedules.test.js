"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const DOMAINS = path.join(__dirname, "..", "domains");

/** Every onSchedule in domains/, as {name, schedule, timeZone}. */
function schedules() {
  const out = [];
  for (const file of fs.readdirSync(DOMAINS).filter((f) => f.endsWith(".js"))) {
    const src = fs.readFileSync(path.join(DOMAINS, file), "utf8");
    for (const m of src.matchAll(/exports\.(\w+)\s*=\s*onSchedule\(\s*\{([^}]*)\}/g)) {
      const body = m[2];
      const schedule = (body.match(/schedule:\s*"([^"]+)"/) || [])[1] || "";
      const timeZone = (body.match(/timeZone:\s*"([^"]+)"/) || [])[1] || "";
      out.push({ name: m[1], file, schedule, timeZone });
    }
  }
  return out;
}

test("a job that runs daily or less often uses a wall-clock time, not an interval", () => {
  // "every 24 hours" is measured from the last deploy, not from midnight. So
  // every `firebase deploy --only functions` pushes such a job another day
  // out, and a project that deploys most days starves it indefinitely.
  //
  // This is not hypothetical: cleanupRateLimits and sendReengagementNudges
  // were the only two written that way, and on 2026-09-06 the scheduler showed
  // both with a last attempt of 2026-09-04 and a next run exactly 24 hours
  // after a DEPLOYMENT_ROLLOUT. Two missed days, no error anywhere. Every
  // other daily job already used "every day HH:MM" and every one of those was
  // firing.
  //
  // Short intervals are fine — a deploy costs them minutes.
  const all = schedules();
  assert.ok(all.length > 10, `found only ${all.length} scheduled jobs — check the parser`);

  const offenders = [];
  for (const job of all) {
    const hours = (job.schedule.match(/^every (\d+) hours?$/) || [])[1];
    const days = (job.schedule.match(/^every (\d+) days?$/) || [])[1];
    if ((hours && Number(hours) >= 12) || days) {
      offenders.push(`${job.name} (${job.file}): "${job.schedule}" — use "every day HH:MM"`);
    }
  }
  assert.deepEqual(offenders, [], offenders.join("\n"));
});

test("every wall-clock schedule names a timezone", () => {
  // "every day 02:00" with no timeZone is 02:00 UTC, which is 06:30 in Kabul —
  // the middle of the morning for a job written to run overnight. The zone is
  // what makes the time mean what it says.
  const offenders = schedules()
    .filter((j) => /^every day \d/.test(j.schedule) || /^\d/.test(j.schedule))
    .filter((j) => j.timeZone !== "Asia/Kabul")
    .map((j) => `${j.name} (${j.file}): "${j.schedule}" has timeZone "${j.timeZone || "(none)"}"`);
  assert.deepEqual(offenders, [], offenders.join("\n"));
});
