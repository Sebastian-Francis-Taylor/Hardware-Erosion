| Optimization                           | Cycles   | Savings | % Improvement |
|----------------------------------------|----------|---------|---------------|
| Baseline (original)                    | 1672     | -       | -             |
| + Early exit on black neighbor         | 1506     | 166     | 9.9%          |
| + Skip border pixel reads              | 1430     | 76      | 5.0%          |
| + Spatial locality caching (left)      | 1312     | 118     | 8.3%          |
| + Line buffer (top neighbor cache)     | 1244     | 68      | 5.2%          |
| + Right neighbor caching (as center)   | 1210     | 34      | 2.7%          |
| **Total improvement**                  | **1210** | **462** | **27.6%**     |
