package hbl2demo

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._
import coupledL2.{EnableCHI, L2ParamKey,MatrixDataBundle,MatrixKey}

import coupledL2.tl2tl.TL2TLCoupledL2
import coupledL2.tl2chi.{CHIIssue, PortIO, TL2CHICoupledL2}
import huancun.BankBitsKey
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink.TLPermissions._
import coupledL2._

import hbl2demo.AMUParameter
import hbl2demo.AMUBundle
import hbl2demo.RegInfo
import hbl2demo.AMUCore

class AMUIO(implicit p: Parameters) extends Bundle {
  val init_fire = Input(Bool())
  val ld_fire   = Input(Bool())
  val st_fire   = Input(Bool())
  val init_done = Output(Bool())
  val ld_done   = Output(Bool())
  val st_done   = Output(Bool())
  
  val reg_in  = Input(new RegInfo)
  val reg_out = Output(new RegInfo)
  
  val matrix_data_in = Vec(8, Flipped(DecoupledIO(new MatrixDataBundle())))  
}


class AMUImp(outer: AMU) extends LazyModuleImp(outer) {
  val io = IO(new AMUIO)
  val amucore = Module(new AMUCore)

  // connect AMUCore
  amucore.io.init_fire := io.init_fire
  amucore.io.ld_fire   := io.ld_fire
  amucore.io.st_fire   := io.st_fire
  io.init_done := amucore.io.init_done
  io.ld_done := amucore.io.ld_done
  io.st_done := amucore.io.st_done

  amucore.io.reg_in := io.reg_in
  io.reg_out := amucore.io.reg_out
  
  // channel M: no diplomacy
  private val matrix_data_in = io.matrix_data_in
  for (i <- 0 until 8) {
    amucore.io.tl.hbl2_tl(i).m.m_valid := matrix_data_in(i).valid
    matrix_data_in(i).ready := true.B // AMU is receiver in M channel
    matrix_data_in(i).bits.data := amucore.io.tl.hbl2_tl(i).m.m_data
    matrix_data_in(i).bits.sourceId := amucore.io.tl.hbl2_tl(i).m.m_source
  }

  // channel A & D: diplomatic node is in outer AMU
  outer.matrix_nodes.zipWithIndex.foreach { case (matrix_node, i) =>
    val (bus, edge) = matrix_node.out.head

    amucore.io.tl.hbl2_tl(i).a.a_ready := bus.a.ready
    bus.a.valid := amucore.io.tl.hbl2_tl(i).a.a_valid
    bus.a.bits.opcode := amucore.io.tl.hbl2_tl(i).a.a_opcode
    bus.a.bits.param := amucore.io.tl.hbl2_tl(i).a.a_param
    bus.a.bits.size := amucore.io.tl.hbl2_tl(i).a.a_size
    bus.a.bits.source := amucore.io.tl.hbl2_tl(i).a.a_source
    bus.a.bits.address := amucore.io.tl.hbl2_tl(i).a.a_address
    bus.a.bits.user := amucore.io.tl.hbl2_tl(i).a.a_user_matrix
    bus.a.bits.mask := amucore.io.tl.hbl2_tl(i).a.a_mask
    bus.a.bits.data := amucore.io.tl.hbl2_tl(i).a.a_data
    bus.a.bits.corrupt := amucore.io.tl.hbl2_tl(i).a.a_corrupt

    amucore.io.tl.hbl2_tl(i).d.d_valid := bus.d.valid
    bus.d.ready := amucore.io.tl.hbl2_tl(i).d.d_ready
    bus.d.bits.opcode := amucore.io.tl.hbl2_tl(i).d.d_opcode
    bus.d.bits.param := amucore.io.tl.hbl2_tl(i).d.d_param
    bus.d.bits.size := amucore.io.tl.hbl2_tl(i).d.d_size
    bus.d.bits.source := amucore.io.tl.hbl2_tl(i).d.d_source  
    bus.d.bits.sink := amucore.io.tl.hbl2_tl(i).d.d_sink
    bus.d.bits.denied := amucore.io.tl.hbl2_tl(i).d.d_denied
    bus.d.bits.data := amucore.io.tl.hbl2_tl(i).d.d_data
    bus.d.bits.corrupt := amucore.io.tl.hbl2_tl(i).d.d_corrupt
  }
}