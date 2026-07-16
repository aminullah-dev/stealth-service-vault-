"use strict";

// Pure rating aggregation, extracted so the salon-rating recompute trigger can be
// unit-tested without Firebase. Averages the valid (finite, > 0) review ratings
// and returns 0 when there are none, so a salon with no scored reviews reads 0
// rather than NaN.
function averageRating(reviews) {
  let sum = 0;
  let n = 0;
  for (const r of reviews || []) {
    const v = Number(r && r.rating);
    if (Number.isFinite(v) && v > 0) {
      sum += v;
      n += 1;
    }
  }
  return n > 0 ? sum / n : 0;
}

module.exports = { averageRating };
