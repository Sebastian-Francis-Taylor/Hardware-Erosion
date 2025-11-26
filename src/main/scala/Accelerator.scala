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

  // State machine states - fully synchronous, one action per cycle
  val idle :: set_center_addr :: read_center :: set_neighbor_addr :: read_neighbor :: write :: done :: Nil = Enum(7)
  val stateReg = RegInit(idle)

  // Coordinate registers
  val xReg = RegInit(0.U(5.W))  // 0-19 needs 5 bits
  val yReg = RegInit(0.U(5.W))

  // Data registers
  val centerPixelReg = RegInit(0.U(32.W))
  val neighborCounter = RegInit(0.U(3.W))  // 0-3 for 4 neighbors
  val erodeFlag = RegInit(false.B)  // True if any neighbor is black

  // Output register
  val outputPixelReg = RegInit(0.U(32.W))

  // Default outputs
  io.done := false.B
  io.writeEnable := false.B
  io.address := 0.U
  io.dataWrite := 0.U

  // Helper functions for address calculation
  def pixelAddress(x: UInt, y: UInt): UInt = x + (y * 20.U)
  def outputAddress(x: UInt, y: UInt): UInt = 400.U + x + (y * 20.U)

  // Border detection (combinational)
  val isBorder = (xReg === 0.U) || (xReg === 19.U) || (yReg === 0.U) || (yReg === 19.U)

  // State machine - fully synchronous, iterative
  switch(stateReg) {
    is(idle) {
      when(io.start) {
        stateReg := set_center_addr
        xReg := 0.U
        yReg := 0.U
      }
    }

    // CYCLE 1: Set address for center pixel
    is(set_center_addr) {
      io.address := pixelAddress(xReg, yReg)
      stateReg := read_center
    }

    // CYCLE 2: Read center pixel data (must keep address set!)
    is(read_center) {
      io.address := pixelAddress(xReg, yReg)  // Keep address set for read
      centerPixelReg := io.dataRead

      // Fast path for border pixels: always output 0
      when(isBorder) {
        outputPixelReg := 0.U
        stateReg := write
      }
      // Fast path for black center pixels: always output 0
      .elsewhen(io.dataRead === 0.U) {
        outputPixelReg := 0.U
        stateReg := write
      }
      // White inner pixel: need to check neighbors
      .otherwise {
        neighborCounter := 0.U
        erodeFlag := false.B
        stateReg := set_neighbor_addr
      }
    }

    // CYCLE N: Set address for current neighbor
    is(set_neighbor_addr) {
      val neighborAddr = WireDefault(0.U(16.W))

      switch(neighborCounter) {
        is(0.U) { neighborAddr := pixelAddress(xReg - 1.U, yReg) }      // left
        is(1.U) { neighborAddr := pixelAddress(xReg + 1.U, yReg) }      // right
        is(2.U) { neighborAddr := pixelAddress(xReg, yReg - 1.U) }      // up
        is(3.U) { neighborAddr := pixelAddress(xReg, yReg + 1.U) }      // down
      }

      io.address := neighborAddr
      stateReg := read_neighbor
    }

    // CYCLE N+1: Read neighbor data (must keep address set!)
    is(read_neighbor) {
      // Keep setting the address for the current neighbor
      val neighborAddr = WireDefault(0.U(16.W))
      switch(neighborCounter) {
        is(0.U) { neighborAddr := pixelAddress(xReg - 1.U, yReg) }      // left
        is(1.U) { neighborAddr := pixelAddress(xReg + 1.U, yReg) }      // right
        is(2.U) { neighborAddr := pixelAddress(xReg, yReg - 1.U) }      // up
        is(3.U) { neighborAddr := pixelAddress(xReg, yReg + 1.U) }      // down
      }
      io.address := neighborAddr

      // Check if neighbor is black (0)
      when(io.dataRead === 0.U) {
        erodeFlag := true.B
      }

      // Move to next neighbor or finish
      when(neighborCounter === 3.U) {
        // All 4 neighbors checked, determine output
        // Must check BOTH erodeFlag (from prev neighbors) AND current read (4th neighbor)
        when(erodeFlag || io.dataRead === 0.U) {
          outputPixelReg := 0.U  // Erode to black
        }.otherwise {
          outputPixelReg := 255.U  // Keep white
        }
        stateReg := write
      }.otherwise {
        neighborCounter := neighborCounter + 1.U
        stateReg := set_neighbor_addr
      }
    }

    is(write) {
      // CYCLE N: Write result to output location
      io.address := outputAddress(xReg, yReg)
      io.dataWrite := outputPixelReg
      io.writeEnable := true.B

      // Move to next pixel
      when(xReg === 19.U && yReg === 19.U) {
        // Finished all pixels
        stateReg := done
      }.elsewhen(xReg === 19.U) {
        // End of row, move to next row
        xReg := 0.U
        yReg := yReg + 1.U
        stateReg := set_center_addr
      }.otherwise {
        // Move to next column
        xReg := xReg + 1.U
        stateReg := set_center_addr
      }
    }

    is(done) {
      io.done := true.B
    }
  }
}
