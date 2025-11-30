# Optimisation Results

## Incremental Optimisations (cellsImage)

| Optimisation                           | Cycles | Savings | % Improvement |
|----------------------------------------|--------|---------|---------------|
| Baseline                               | 1672   | -       | -             |
| + Early exit on black neighbour        | 1506   | 166     | 9.9%          |
| + Skip border reads                    | 1430*  | 76      | 5.0%          |
| + Left neighbour cache                 | 1312*  | 118     | 8.3%          |
| + Line buffer (top cache)              | 1244   | 68      | 5.2%          |
| + Right neighbour cache                | 1210   | 34      | 2.7%          |
| **Total**                              | **1210** | **462** | **27.6%**   |

*Estimated from git b13bd62

## All Images - All Versions

| Version          | blackImage | whiteImage | cellsImage | borderCellsImage |
|------------------|------------|------------|------------|------------------|
| Baseline         | 1200       | 3792       | 1672       | 1584             |
| + Early exit     | 1200       | 3792       | 1506       | 1466             |
| + Caches*        | 1124       | 2382       | 1244       | 1242             |
| **Final**        | **1124**   | **2093**   | **1210**   | **1214**         |

*Skip border + left cache + line buffer

## Summary

- **Best improvement:** whiteImage 44.8% (3792 → 2093 cycles)
- **Realistic improvement:** cellsImage 27.6% (1672 → 1210 cycles)
- **Key optimization:** Caching (left/top/right) eliminates redundant memory reads
