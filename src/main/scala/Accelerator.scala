import chisel3._
import chisel3.util._

class Accelerator extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done = Output(Bool())

    val address = Output(UInt (16.W))
    val dataRead = Input(UInt (32.W))
    val writeEnable = Output(Bool ())
    val dataWrite = Output(UInt (32.W))

  })

  // states
  val idle :: set_center_addr :: read_center :: set_neighbor_addr :: read_neighbor :: write :: done :: Nil = Enum(7)
  val stateReg = RegInit(idle)

  // registers
  val xReg = RegInit(0.U(5.W))
  val yReg = RegInit(0.U(5.W))
  val centerPixelReg = RegInit(0.U(32.W))
  val neighborCounter = RegInit(0.U(3.W))
  val outputPixelReg = RegInit(0.U(32.W))

  // Spatial locality cache - stores previous center pixel for reuse as left neighbor
  val cachedLeftNeighbor = RegInit(0.U(32.W))
  val canUseCachedLeft = RegInit(false.B)

  // Line buffer - stores entire previous row for top neighbor reuse
  val lineBuffer = RegInit(VecInit(Seq.fill(20)(0.U(32.W))))
  val lineBufferValid = RegInit(false.B)

  // default
  io.done := false.B
  io.writeEnable := false.B
  io.address := 0.U
  io.dataWrite := 0.U

  // address calculation
  def pixelAddress(x: UInt, y: UInt): UInt = x + (y * 20.U)
  def outputAddress(x: UInt, y: UInt): UInt = 400.U + x + (y * 20.U)

  val isBorder = (xReg === 0.U) || (xReg === 19.U) || (yReg === 0.U) || (yReg === 19.U)

  // fsmd
  switch(stateReg) {
    is(idle) {
      when(io.start) {
        stateReg := set_center_addr
        xReg := 0.U
        yReg := 0.U
      }
    }

    is(set_center_addr) {
      when(isBorder) {
        // Border pixels are always black - skip reading entirely
        outputPixelReg := 0.U
        stateReg := write
      }.otherwise {
        io.address := pixelAddress(xReg, yReg)
        stateReg := read_center
      }
    }

    is(read_center) {
      io.address := pixelAddress(xReg, yReg)
      centerPixelReg := io.dataRead

      // Store in line buffer for next row's top neighbor
      lineBuffer(xReg) := io.dataRead

      // Cache center pixel for next pixel's left neighbor (if not last in row)
      when(xReg < 19.U) {
        cachedLeftNeighbor := io.dataRead
        canUseCachedLeft := true.B
      }

      when(io.dataRead === 0.U) {
        // Black center pixel stays black
        outputPixelReg := 0.U
        stateReg := write
      }
      .otherwise {
        // White center pixel - need to check neighbors
        // Check cached neighbors first (left and top)
        val cachedLeftIsBlack = canUseCachedLeft && cachedLeftNeighbor === 0.U
        val cachedTopIsBlack = lineBufferValid && lineBuffer(xReg) === 0.U

        when(cachedLeftIsBlack || cachedTopIsBlack) {
          // Found black neighbor in cache - erode immediately
          outputPixelReg := 0.U
          stateReg := write
        }.elsewhen(canUseCachedLeft && lineBufferValid) {
          // Both left and top cached and white - skip to right neighbor
          neighborCounter := 1.U
          stateReg := set_neighbor_addr
        }.elsewhen(canUseCachedLeft) {
          // Only left cached - skip to right, will check top later
          neighborCounter := 1.U
          stateReg := set_neighbor_addr
        }.elsewhen(lineBufferValid) {
          // Only top cached - start from left, will skip top later
          neighborCounter := 0.U
          stateReg := set_neighbor_addr
        }.otherwise {
          // No cache - start checking from left neighbor
          neighborCounter := 0.U
          stateReg := set_neighbor_addr
        }
      }
    }

    is(set_neighbor_addr) {
      val neighborAddr = WireDefault(0.U(16.W))

      // Skip top neighbor (index 2) if already cached in line buffer
      val shouldSkipTop = (neighborCounter === 2.U) && lineBufferValid

      when(shouldSkipTop) {
        // Skip top neighbor - jump to bottom neighbor
        neighborCounter := 3.U
        neighborAddr := pixelAddress(xReg, yReg + 1.U)
      }.otherwise {
        switch(neighborCounter) {
          is(0.U) { neighborAddr := pixelAddress(xReg - 1.U, yReg) }
          is(1.U) { neighborAddr := pixelAddress(xReg + 1.U, yReg) }
          is(2.U) { neighborAddr := pixelAddress(xReg, yReg - 1.U) }
          is(3.U) { neighborAddr := pixelAddress(xReg, yReg + 1.U) }
        }
      }

      io.address := neighborAddr
      stateReg := read_neighbor
    }

    is(read_neighbor) {
      val neighborAddr = WireDefault(0.U(16.W))
      switch(neighborCounter) {
        is(0.U) { neighborAddr := pixelAddress(xReg - 1.U, yReg) }
        is(1.U) { neighborAddr := pixelAddress(xReg + 1.U, yReg) }
        is(2.U) { neighborAddr := pixelAddress(xReg, yReg - 1.U) }
        is(3.U) { neighborAddr := pixelAddress(xReg, yReg + 1.U) }
      }
      io.address := neighborAddr

      when(io.dataRead === 0.U) {
        // Found black neighbor - exit immediately!
        outputPixelReg := 0.U
        stateReg := write
      }
      .elsewhen(neighborCounter === 3.U) {
        // Checked all 4 neighbors, none were black
        outputPixelReg := 255.U
        stateReg := write
      }
      .otherwise {
        // Keep checking next neighbor
        neighborCounter := neighborCounter + 1.U
        stateReg := set_neighbor_addr
      }
    }

    is(write) {
      io.address := outputAddress(xReg, yReg)
      io.dataWrite := outputPixelReg
      io.writeEnable := true.B

      when(xReg === 19.U && yReg === 19.U) {
        stateReg := done
      }.elsewhen(xReg === 19.U) {
        // End of row - invalidate left cache, enable line buffer for new row
        xReg := 0.U
        yReg := yReg + 1.U
        canUseCachedLeft := false.B
        // Line buffer becomes valid after first row (y=0) is complete
        when(yReg === 0.U) {
          lineBufferValid := true.B
        }
        stateReg := set_center_addr
      }.otherwise {
        xReg := xReg + 1.U
        stateReg := set_center_addr
      }
    }

    is(done) {
      io.done := true.B
    }
  }
}
