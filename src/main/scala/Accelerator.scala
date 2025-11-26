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
  val erodeFlag = RegInit(false.B)
  val outputPixelReg = RegInit(0.U(32.W))

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
      io.address := pixelAddress(xReg, yReg)
      stateReg := read_center
    }

    is(read_center) {
      io.address := pixelAddress(xReg, yReg)
      centerPixelReg := io.dataRead

      when(isBorder) {
        outputPixelReg := 0.U
        stateReg := write
      }
      .elsewhen(io.dataRead === 0.U) {
        outputPixelReg := 0.U
        stateReg := write
      }
      .otherwise {
        neighborCounter := 0.U
        erodeFlag := false.B
        stateReg := set_neighbor_addr
      }
    }

    is(set_neighbor_addr) {
      val neighborAddr = WireDefault(0.U(16.W))

      switch(neighborCounter) {
        is(0.U) { neighborAddr := pixelAddress(xReg - 1.U, yReg) }
        is(1.U) { neighborAddr := pixelAddress(xReg + 1.U, yReg) }
        is(2.U) { neighborAddr := pixelAddress(xReg, yReg - 1.U) }
        is(3.U) { neighborAddr := pixelAddress(xReg, yReg + 1.U) }
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
        erodeFlag := true.B
      }

      when(neighborCounter === 3.U) {
        when(erodeFlag || io.dataRead === 0.U) {
          outputPixelReg := 0.U
        }.otherwise {
          outputPixelReg := 255.U
        }
        stateReg := write
      }.otherwise {
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
        xReg := 0.U
        yReg := yReg + 1.U
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
