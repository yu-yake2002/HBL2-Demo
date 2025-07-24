package hbl2demo

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import coupledL2.{MatrixField, MatrixKey, MatrixDataBundle}

/**
 * 2x2 Crossbar Switch for Benes Network
 * Routes data based on routing bit
 */
class Crossbar2x2(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val in = Vec(2, Flipped(DecoupledIO(new MatrixDataBundle())))
    val out = Vec(2, DecoupledIO(new MatrixDataBundle()))
    val routing_bit = Input(Bool()) // 0: straight, 1: cross
  })

  // Default connections (straight)
  io.out(0) <> io.in(0)
  io.out(1) <> io.in(1)

  // Cross connections when routing_bit is 1
  when (io.routing_bit) {
    io.out(0) <> io.in(1)
    io.out(1) <> io.in(0)
  }
}

/**
 * 8x8 Benes Network
 * Uses high 3 bits of sourceId for routing
 * Standard Benes network topology with 5 stages
 */
class BenesNetwork8x8(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val in = Vec(8, Flipped(DecoupledIO(new MatrixDataBundle())))
    val out = Vec(8, DecoupledIO(new MatrixDataBundle()))
  })

  // Extract routing bits from sourceId (high 3 bits)
  val routing_bits = Wire(Vec(8, UInt(3.W)))
  for (i <- 0 until 8) {
    routing_bits(i) := io.in(i).bits.sourceId(7, 5) // High 3 bits
  }

  // Stage 1: 4 crossbars (0-1, 2-3, 4-5, 6-7)
  val stage1 = Seq.fill(4)(Module(new Crossbar2x2))
  for (i <- 0 until 4) {
    stage1(i).io.in(0) <> io.in(i * 2)
    stage1(i).io.in(1) <> io.in(i * 2 + 1)
    stage1(i).io.routing_bit := routing_bits(i * 2)(2) // Use bit 2
  }

  // Stage 2: 4 crossbars with shuffle pattern
  val stage2 = Seq.fill(4)(Module(new Crossbar2x2))
  // Connections: 0->0, 1->2, 2->4, 3->6, 4->1, 5->3, 6->5, 7->7
  stage2(0).io.in(0) <> stage1(0).io.out(0) // 0->0
  stage2(1).io.in(0) <> stage1(0).io.out(1) // 1->2
  stage2(2).io.in(0) <> stage1(1).io.out(0) // 2->4
  stage2(3).io.in(0) <> stage1(1).io.out(1) // 3->6
  stage2(0).io.in(1) <> stage1(2).io.out(0) // 4->1
  stage2(1).io.in(1) <> stage1(2).io.out(1) // 5->3
  stage2(2).io.in(1) <> stage1(3).io.out(0) // 6->5
  stage2(3).io.in(1) <> stage1(3).io.out(1) // 7->7

  // Set routing bits for stage 2
  stage2(0).io.routing_bit := routing_bits(0)(1) // Use bit 1
  stage2(1).io.routing_bit := routing_bits(1)(1)
  stage2(2).io.routing_bit := routing_bits(2)(1)
  stage2(3).io.routing_bit := routing_bits(3)(1)

  // Stage 3: 4 crossbars with perfect shuffle
  val stage3 = Seq.fill(4)(Module(new Crossbar2x2))
  // Connections: 0->0, 2->1, 4->2, 6->3, 1->4, 3->5, 5->6, 7->7
  stage3(0).io.in(0) <> stage2(0).io.out(0) // 0->0
  stage3(0).io.in(1) <> stage2(1).io.out(0) // 2->1
  stage3(1).io.in(0) <> stage2(2).io.out(0) // 4->2
  stage3(1).io.in(1) <> stage2(3).io.out(0) // 6->3
  stage3(2).io.in(0) <> stage2(0).io.out(1) // 1->4
  stage3(2).io.in(1) <> stage2(1).io.out(1) // 3->5
  stage3(3).io.in(0) <> stage2(2).io.out(1) // 5->6
  stage3(3).io.in(1) <> stage2(3).io.out(1) // 7->7

  // Set routing bits for stage 3
  stage3(0).io.routing_bit := routing_bits(0)(0) // Use bit 0
  stage3(1).io.routing_bit := routing_bits(2)(0)
  stage3(2).io.routing_bit := routing_bits(1)(0)
  stage3(3).io.routing_bit := routing_bits(3)(0)

  // Stage 4: 4 crossbars (reverse shuffle)
  val stage4 = Seq.fill(4)(Module(new Crossbar2x2))
  // Connections: 0->0, 1->2, 2->4, 3->6, 4->1, 5->3, 6->5, 7->7
  stage4(0).io.in(0) <> stage3(0).io.out(0) // 0->0
  stage4(1).io.in(0) <> stage3(0).io.out(1) // 1->2
  stage4(2).io.in(0) <> stage3(1).io.out(0) // 2->4
  stage4(3).io.in(0) <> stage3(1).io.out(1) // 3->6
  stage4(0).io.in(1) <> stage3(2).io.out(0) // 4->1
  stage4(1).io.in(1) <> stage3(2).io.out(1) // 5->3
  stage4(2).io.in(1) <> stage3(3).io.out(0) // 6->5
  stage4(3).io.in(1) <> stage3(3).io.out(1) // 7->7

  // Set routing bits for stage 4
  stage4(0).io.routing_bit := routing_bits(0)(1) // Use bit 1
  stage4(1).io.routing_bit := routing_bits(1)(1)
  stage4(2).io.routing_bit := routing_bits(2)(1)
  stage4(3).io.routing_bit := routing_bits(3)(1)

  // Stage 5: 4 crossbars (reverse butterfly)
  val stage5 = Seq.fill(4)(Module(new Crossbar2x2))
  for (i <- 0 until 4) {
    stage5(i).io.in(0) <> stage4(i).io.out(0)
    stage5(i).io.in(1) <> stage4(i).io.out(1)
    stage5(i).io.routing_bit := routing_bits(i * 2)(2) // Use bit 2
  }

  // Connect to outputs
  for (i <- 0 until 4) {
    io.out(i * 2) <> stage5(i).io.out(0)
    io.out(i * 2 + 1) <> stage5(i).io.out(1)
  }
}

/**
 * Simplified 8x8 Benes Network with 3 stages
 * This is a more practical implementation for 8x8 networks
 */
class BenesNetwork8x8Simple(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val in = Vec(8, Flipped(DecoupledIO(new MatrixDataBundle())))
    val out = Vec(8, DecoupledIO(new MatrixDataBundle()))
  })

  // Extract routing bits from sourceId (high 3 bits)
  val routing_bits = Wire(Vec(8, UInt(3.W)))
  for (i <- 0 until 8) {
    routing_bits(i) := io.in(i).bits.sourceId(7, 5) // High 3 bits
  }

  // Stage 1: Butterfly pattern
  val stage1 = Seq.fill(4)(Module(new Crossbar2x2))
  for (i <- 0 until 4) {
    stage1(i).io.in(0) <> io.in(i * 2)
    stage1(i).io.in(1) <> io.in(i * 2 + 1)
    stage1(i).io.routing_bit := routing_bits(i * 2)(2) // Use bit 2
  }

  // Stage 2: Perfect shuffle
  val stage2 = Seq.fill(4)(Module(new Crossbar2x2))
  // Perfect shuffle pattern: 0->0, 1->2, 2->4, 3->6, 4->1, 5->3, 6->5, 7->7
  stage2(0).io.in(0) <> stage1(0).io.out(0) // 0->0
  stage2(1).io.in(0) <> stage1(0).io.out(1) // 1->2
  stage2(2).io.in(0) <> stage1(1).io.out(0) // 2->4
  stage2(3).io.in(0) <> stage1(1).io.out(1) // 3->6
  stage2(0).io.in(1) <> stage1(2).io.out(0) // 4->1
  stage2(1).io.in(1) <> stage1(2).io.out(1) // 5->3
  stage2(2).io.in(1) <> stage1(3).io.out(0) // 6->5
  stage2(3).io.in(1) <> stage1(3).io.out(1) // 7->7

  // Set routing bits for stage 2
  stage2(0).io.routing_bit := routing_bits(0)(1) // Use bit 1
  stage2(1).io.routing_bit := routing_bits(1)(1)
  stage2(2).io.routing_bit := routing_bits(2)(1)
  stage2(3).io.routing_bit := routing_bits(3)(1)

  // Stage 3: Reverse butterfly
  val stage3 = Seq.fill(4)(Module(new Crossbar2x2))
  for (i <- 0 until 4) {
    stage3(i).io.in(0) <> stage2(i).io.out(0)
    stage3(i).io.in(1) <> stage2(i).io.out(1)
    stage3(i).io.routing_bit := routing_bits(i * 2)(0) // Use bit 0
  }

  // Connect to outputs
  for (i <- 0 until 4) {
    io.out(i * 2) <> stage3(i).io.out(0)
    io.out(i * 2 + 1) <> stage3(i).io.out(1)
  }
}

/**
 * Routing logic generator for Benes network
 * This module generates the routing bits based on source and destination
 */
class BenesRoutingLogic(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val source = Input(UInt(3.W))  // Source port (0-7)
    val dest = Input(UInt(3.W))    // Destination port (0-7)
    val routing_bits = Output(UInt(3.W)) // Routing bits for 3 stages
  })

  // Benes network routing algorithm
  // For 8x8 network, we use 3 routing bits for 3 stages
  val stage1_bit = io.source(2) ^ io.dest(2)
  val stage2_bit = io.source(1) ^ io.dest(1)
  val stage3_bit = io.source(0) ^ io.dest(0)

  io.routing_bits := Cat(stage1_bit, stage2_bit, stage3_bit)
}

/**
 * Test module for Benes Network
 */
class BenesNetworkTest(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val in = Vec(8, Flipped(DecoupledIO(new MatrixDataBundle())))
    val out = Vec(8, DecoupledIO(new MatrixDataBundle()))
    val test_mode = Input(Bool())
  })

  val benes_network = Module(new BenesNetwork8x8)
  val benes_network_simple = Module(new BenesNetwork8x8Simple)

  // Select between two implementations
  when (io.test_mode) {
    // Use simple implementation
    benes_network_simple.io.in <> io.in
    io.out <> benes_network_simple.io.out
  } .otherwise {
    // Use full implementation
    benes_network.io.in <> io.in
    io.out <> benes_network.io.out
  }
} 