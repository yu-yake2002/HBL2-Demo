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


class AMUImp(outer: AMU, params: TLBundleParameters) extends LazyModuleImp(outer) {
  val io = IO(new AMUIO)
  val amucore = Module(new AMUCore()(p, params))

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
    matrix_data_in(i).ready := true.B // AMU is receiver in M channel
    amucore.io.tl.hbl2_tl(i).m.valid := matrix_data_in(i).valid
    amucore.io.tl.hbl2_tl(i).m.bits.data := matrix_data_in(i).bits.data
    amucore.io.tl.hbl2_tl(i).m.bits.sourceId := matrix_data_in(i).bits.sourceId
  }

  // channel A & D: diplomatic node is in outer AMU
  outer.matrix_nodes.zipWithIndex.foreach { case (matrix_node, i) =>
    val (bus, edge) = matrix_node.out.head

    amucore.io.tl.hbl2_tl(i).a.ready := bus.a.ready
    bus.a.valid                      := amucore.io.tl.hbl2_tl(i).a.valid
    bus.a.bits.opcode                := amucore.io.tl.hbl2_tl(i).a.bits.opcode
    bus.a.bits.param                 := amucore.io.tl.hbl2_tl(i).a.bits.param
    bus.a.bits.size                  := amucore.io.tl.hbl2_tl(i).a.bits.size
    bus.a.bits.source                := amucore.io.tl.hbl2_tl(i).a.bits.source
    bus.a.bits.address               := amucore.io.tl.hbl2_tl(i).a.bits.address
    bus.a.bits.user(MatrixKey)       := 1.U  // amucore.io.tl.hbl2_tl(i).a.bits.user(MatrixKey)
    bus.a.bits.mask                  := amucore.io.tl.hbl2_tl(i).a.bits.mask
    bus.a.bits.data                  := amucore.io.tl.hbl2_tl(i).a.bits.data
    bus.a.bits.corrupt               := amucore.io.tl.hbl2_tl(i).a.bits.corrupt

    
    bus.d.ready                              := amucore.io.tl.hbl2_tl(i).d.ready
    amucore.io.tl.hbl2_tl(i).d.valid         := bus.d.valid
    amucore.io.tl.hbl2_tl(i).d.bits.opcode   := bus.d.bits.opcode
    amucore.io.tl.hbl2_tl(i).d.bits.param    := bus.d.bits.param
    amucore.io.tl.hbl2_tl(i).d.bits.size     := bus.d.bits.size
    amucore.io.tl.hbl2_tl(i).d.bits.source   := bus.d.bits.source  
    amucore.io.tl.hbl2_tl(i).d.bits.sink     := bus.d.bits.sink
    amucore.io.tl.hbl2_tl(i).d.bits.denied   := bus.d.bits.denied
    amucore.io.tl.hbl2_tl(i).d.bits.data     := bus.d.bits.data
    amucore.io.tl.hbl2_tl(i).d.bits.corrupt  := bus.d.bits.corrupt
  }
}