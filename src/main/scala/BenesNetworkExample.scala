package hbl2demo

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import coupledL2.{MatrixField, MatrixKey, MatrixDataBundle}
import freechips.rocketchip.tilelink.TLMessages
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import common.Consts
import AME._

/**
 * Example of how to integrate Benes Network with AME
 * This shows how to replace the current matrix_data_in handling with Benes network
 */
class AMEWithBenesNetwork(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val matrix_data_in = Vec(8, Flipped(DecoupledIO(new MatrixDataBundle())))
    val matrix_data_out = Vec(8, DecoupledIO(new MatrixDataBundle()))
    val channel_id_bits = Input(UInt(3.W))
  })

  // Instantiate Benes Network
  val benes_network = Module(new BenesNetwork8x8Simple)
  
  // Connect inputs to Benes network
  benes_network.io.in <> io.matrix_data_in
  
  // Connect Benes network outputs to final outputs
  io.matrix_data_out <> benes_network.io.out
}

/**
 * Enhanced AME implementation with Benes Network
 * This replaces the current readback_arbiters approach with Benes network routing
 */
class AMEImpWithBenes(outer: AMEModule)(implicit p: Parameters) extends LazyModuleImp(outer) {
  val io = IO(new AMEIO)
  
  // Get TileLink interfaces for all 8 matrix nodes
  val (tls, edges) = outer.matrix_nodes.map(_.out.head).unzip

  val ame = Module(new AME)
  ame.io.Uop_io <> io.Uop_io
  ame.io.writeAll <> io.writeAll
  ame.io.readAll <> io.readAll
  io.sigDone := ame.io.sigDone

  // M channel handling (for Get responses)
  val mlu_read_io = ame.io.MLU_L2_io.Cacheline_Read_io
  val mlu_readback_io = ame.io.MLU_L2_io.Cacheline_ReadBack_io
  val msu_write_io = ame.io.MSU_L2_io.Cacheline_Write_io
  val msu_writeback_io = ame.io.MSU_L2_io.Cacheline_WriteBack_io
  val matrix_data_in = io.matrix_data_in

  val ame_data_bits = Consts.L2_DATA_LEN
  val tl_data_bits = tls(0).params.dataBits
  val write_beats = ame_data_bits / tl_data_bits
  println(s"[AMEModule] write beats: $write_beats, ame_data_bits: $ame_data_bits, tl_data_bits: $tl_data_bits")
  require(write_beats == 2, "write_beats must be 2")

  val channel_id_bits = log2Ceil(mlu_read_io.length)

  // Use mlu_read_io length for unified processing when iterating through mlu_read_io
  // Since msu_write_io length is less than mlu_read_io, extra entries will be optimized away
  val write_inflight = RegInit(VecInit(Seq.fill(mlu_read_io.length)(false.B)))
  require(msu_write_io.length <= mlu_read_io.length, "guard write_inflight indexing")

  tls.foreach { tl =>
    tl.a.valid := false.B
    tl.a.bits := 0.U.asTypeOf(tl.a.bits)
 
    // D channel - Only used for Put responses
    tl.d.ready := true.B

    // We don't use B, C channels for this interface
    tl.b.ready := true.B
    tl.c.valid := false.B
    tl.c.bits := 0.U.asTypeOf(tl.c.bits)
    tl.e.valid := false.B
    tl.e.bits := 0.U.asTypeOf(tl.e.bits)
  }

  // Use Benes Network instead of arbiters for routing
  val benes_network = Module(new BenesNetwork8x8Simple)
  benes_network.io.in <> matrix_data_in

  // Process routed data from Benes network
  for (i <- 0 until mlu_readback_io.length) {
    val routed_data = benes_network.io.out(i)
    
    // Extract channel information from routed data
    val id = routed_data.bits.sourceId(Consts.L2_ID_LEN - 1, 0)
    assert(id < 32.U, "id must be less than 32")
    
    // Connect to MLU readback interface
    mlu_readback_io(i).valid := routed_data.valid
    mlu_readback_io(i).data := routed_data.bits.data.data
    mlu_readback_io(i).id := id
    routed_data.ready := true.B
  }

  // Connect each MLU Cacheline interface to its corresponding TileLink node
  for (i <- 0 until ame.io.MLU_L2_io.Cacheline_Read_io.length) {
    println(s"Connecting MLU Cacheline interface $i to TileLink node")
    val tl = tls(i)
    val edge = edges(i)

    // A channel - Get request handling
    mlu_read_io(i).ready := tl.a.ready && !write_inflight(i)

    when(mlu_read_io(i).valid) {
      val (legal, get_bits) = edge.Get(
        fromSource = Cat(i.U(channel_id_bits.W), mlu_read_io(i).id),
        toAddress = mlu_read_io(i).addr,
        lgSize = 6.U // 64 bytes = 2^6
      )

      tl.a.valid := legal
      tl.a.bits := get_bits
      tl.a.bits.user(MatrixKey):= 1.U// Mark as Matrix request
    }
  }

  // MSU_L2 <> TileLink
  for (i <- 0 until ame.io.MSU_L2_io.Cacheline_Write_io.length) {
    val tl = tls(i)
    val edge = edges(i)

    val beat_count = RegInit(0.U(2.W))
    val next_beat_data = RegInit(0.U(tl_data_bits.W))  // Only latch the hi-half of msu data.
    val msu_addr_reg = RegInit(0.U(Consts.L2_ADDR_LEN.W))

    // MSU_L2_WRITE to TileLink Put
    msu_write_io(i).ready := tl.a.ready && !mlu_read_io(i).valid && !write_inflight(i)
    val msu_write_fire = msu_write_io(i).valid && msu_write_io(i).ready

    // Present the first beat to tilelink A when mlu does not require A.
    when (!mlu_read_io(i).valid && msu_write_io(i).valid && !write_inflight(i)) {
      val (legal, put_bits) = edge.Put(
        fromSource = 0.U,
        toAddress = msu_write_io(i).addr,
        lgSize = log2Ceil(Consts.L2_DATA_LEN / 8).U,
        data = msu_write_io(i).data
      )
      assert(legal, "MSU_L2_WRITE to TileLink Put is illegal")

      tl.a.valid := legal
      tl.a.bits := put_bits
      tl.a.bits.user(MatrixKey) := 1.U  // Mark as Matrix request
    }

    // Register state when msu rised tl_a fires.
    when (tl.a.fire && msu_write_fire) {
        msu_addr_reg := msu_write_io(i).addr
        next_beat_data := msu_write_io(i).data(ame_data_bits - 1, tl_data_bits)
        write_inflight(i) := true.B
    }

    // Next beat.
    when (write_inflight(i)) {
      val (legal, put_bits) = edge.Put(
        fromSource = 0.U,
        toAddress = msu_addr_reg,
        lgSize = log2Ceil(Consts.L2_DATA_LEN / 8).U,
        data = next_beat_data
      )
      assert(legal, "MSU_L2_WRITE to TileLink Put is illegal")

      tl.a.valid := legal
      tl.a.bits := put_bits
      tl.a.bits.user(MatrixKey) := 1.U  // Mark as Matrix request

      when (tl.a.fire) {
        write_inflight(i) := false.B
        beat_count := 0.U
        next_beat_data := 0.U(tl_data_bits.W)
        msu_addr_reg := 0.U(Consts.L2_ADDR_LEN.W)
      }
    }

    // TileLink Ack to MSU_L2_WRITEBACK
    msu_writeback_io(i).valid := false.B
    when (tl.d.valid && (tl.d.bits.opcode === TLMessages.AccessAck || tl.d.bits.opcode === TLMessages.ReleaseAck)) {
      msu_writeback_io(i).valid := true.B
      // printf(s"MSU_L2_WRITEACK: channel=$i\n")
    }
  }
}

/**
 * Configuration for Benes Network routing
 * This defines how sourceId bits are mapped to routing decisions
 */
object BenesRoutingConfig {
  // Routing bit mapping for 8x8 Benes network
  // sourceId[7:5] -> routing_bits[2:0]
  def getRoutingBits(sourceId: UInt): UInt = {
    sourceId(7, 5) // Use high 3 bits for routing
  }
  
  // Alternative routing based on destination channel
  def getRoutingBitsForDest(sourceId: UInt, destChannel: UInt): UInt = {
    val sourceChannel = sourceId(7, 5)
    val routingBits = Wire(UInt(3.W))
    
    // Simple XOR-based routing
    routingBits(2) := sourceChannel(2) ^ destChannel(2)
    routingBits(1) := sourceChannel(1) ^ destChannel(1)
    routingBits(0) := sourceChannel(0) ^ destChannel(0)
    
    routingBits
  }
} 