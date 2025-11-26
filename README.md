# Hardware Erosion Accelerator
FSMD-based hardware accelerator for image erosion in Chisel3.

## Run
```bash
sbt "testOnly SystemTopTester"
```

## Performance
1672 clock cycles for 20x20 cellsimage

## File Structure
```
src/
├── main/scala/
│   ├── Accelerator.scala
│   ├── DataMemory.scala
│   └── SystemTop.scala
└── test/scala/
    ├── SystemTopTester.scala
    └── Images.scala
```

## Architecture
7-state FSMD with fast paths for border and black pixels. White inner pixels check 4 neighbors before deciding erosion.
