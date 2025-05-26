package hbl2demo

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import scala.util.Random

import AME._
import utility.sram._
import common._
import RegFile._
import MMAU._


// Main Test Class
class TestTop_AME(implicit p: Parameters) extends LazyModule {
  val ame = LazyModule(new AMEModule())
  
  lazy val module = new LazyModuleImp(this) {
    val timer = WireDefault(0.U(64.W))
    val logEnable = WireDefault(false.B)
    val clean = WireDefault(false.B)
    val dump = WireDefault(false.B)

    dontTouch(timer)
    dontTouch(logEnable)
    dontTouch(clean)
    dontTouch(dump)
  }
}

class AMETest extends AnyFreeSpec with Matchers {
  "AMEModule should execute matrix operations correctly" in {
    val config = new Config((site, here, up) => {
      case AMEConfigKey => AMEParams(
        numTrRegs = 2,
        numAccRegs = 1,
        dataWidth = 32,
        matrixSize = 16
      )
    })

    val top = DisableMonitors(p => LazyModule(new AMEModule()(p)))(config)
    simulate(top.module) { dut =>
      val ame = dut.ame.module

      // Initialize test data
      AMETestHelper.writeTestDataToAll(AMETestData.A, 0, ame)
      AMETestHelper.writeTestDataToAll(AMETestData.B, 1, ame)
      AMETestHelper.writeTestDataToAll(AMETestData.Ctmp, 4, ame)
      AMETestHelper.writeTestDataToAll(AMETestData.Ctmp, 5, ame)
      AMETestHelper.writeTestDataToAll(AMETestData.Ctmp, 6, ame)
      AMETestHelper.writeTestDataToAll(AMETestData.Ctmp, 7, ame)

      dut.clock.step(1000) // Initial cycles

      // Test Instruction 1
      var cycleCountMMAU = 0
      var cycleCountReady = 0

      // Start first matrix operation
      AMETestHelper.AMEStart(ame, 32, 32, 64, 0, 1, 4, 0, 0, true.B, true.B, false.B)

      // Wait for ready
      while(!ame.io.Uop_io.ShakeHands_io.ready.peek().litToBoolean) {
        dut.clock.step(1)
        cycleCountReady += 1
      }
      dut.clock.step(1)
      println("Instruction 1 executing")

      // Queue next instruction
      AMETestHelper.AMEStart(ame, 32, 32, 64, 0, 1, 5, 0, 0, true.B, true.B, false.B)

      // Wait for completion
      while(!ame.io.sigDone.peek().litToBoolean) {
        dut.clock.step(1)
        cycleCountMMAU += 1
      }
      println(s"Instruction 1: Ready cycles = $cycleCountReady, MMAU cycles = $cycleCountMMAU")

      // Test Instruction 2
      cycleCountReady = 0
      cycleCountMMAU = 0

      while(!ame.io.Uop_io.ShakeHands_io.ready.peek().litToBoolean) {
        dut.clock.step(1)
        cycleCountReady += 1
      }
      dut.clock.step(1)
      println("Instruction 2 executing")

      // Queue next instruction
      AMETestHelper.AMEStart(ame, 32, 32, 64, 0, 1, 6, 0, 0, true.B, true.B, false.B)

      while(!ame.io.sigDone.peek().litToBoolean) {
        dut.clock.step(1)
        cycleCountMMAU += 1
      }
      println(s"Instruction 2: Ready cycles = $cycleCountReady, MMAU cycles = $cycleCountMMAU")

      // Test Instruction 3
      cycleCountReady = 0
      cycleCountMMAU = 0

      while(!ame.io.Uop_io.ShakeHands_io.ready.peek().litToBoolean) {
        dut.clock.step(1)
        cycleCountReady += 1
      }
      dut.clock.step(1)
      println("Instruction 3 executing")

      while(!ame.io.sigDone.peek().litToBoolean) {
        dut.clock.step(1)
        cycleCountMMAU += 1
      }
      println(s"Instruction 3: Ready cycles = $cycleCountReady, MMAU cycles = $cycleCountMMAU")

      // Stop AME
      AMETestHelper.AMEStop(ame)

      // Verify results
      AMETestHelper.readTestDataFromAll(AMETestData.C, 4, ame)
      AMETestHelper.readTestDataFromAll(AMETestData.C, 5, ame)
      AMETestHelper.readTestDataFromAll(AMETestData.C, 6, ame)
    }
  }
}

// Test Object for standalone execution
object TestTop_AME extends App {
  val config = new Config((site, here, up) => {
    case AMEConfigKey => AMEParams(
      numTrRegs = 2,
      numAccRegs = 1,
      dataWidth = 32,
      matrixSize = 16
    )
  })

  val top = DisableMonitors(p => LazyModule(new TestTop_AME()(p)))(config)

  (new ChiselStage).execute(args, Seq(
    ChiselGeneratorAnnotation(() => top.module)
  ))
} 