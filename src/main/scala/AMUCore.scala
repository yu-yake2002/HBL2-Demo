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
import hbl2demo.HBL2_TL
import hbl2demo.RegInfo


class AMUCore_IO extends AMUBundle {
  val init_fire = Input(Bool())
  val ld_fire   = Input(Bool())
  val st_fire   = Input(Bool())
  val init_done = Output(Bool())
  val ld_done   = Output(Bool())
  val st_done   = Output(Bool())
  val tl        = new HBL2_TL
  val reg_out   = Output(new RegInfo)
  val reg_in    = Input(new RegInfo)
}

class AMUCore extends Module with AMUParameter {
  val io = IO(new AMUCore_IO)

  // 8 * 32B vector
  val reg = Reg(Vec(8, UInt(aPutBits.W)))
  // read data: asynchronous
  io.reg_out.reginfo := reg

  /////////////////////////////////////////////////////////////////////////////////////////
  // state machine
  val states   = Enum(10)

  val idle     = states(0)
  val initReg  = states(1)
  val initDone = states(2)

  val ldAReq   = states(3)
  val ldMData  = states(4)
  val ldDone   = states(5)

  val stAReq   = states(6)
  val stDAck   = states(7)
  val stDone   = states(8)

  val state_r  = RegInit(idle)

/////////////////////////////////////////////////////////////////////////////////////////
  switch(state_r) {
    is(idle) {
      when(io.init_fire) {
        state_r := initReg
      }
      when(io.ld_fire) {
        state_r := ldAReq
      }
      when(io.st_fire) {
        state_r := stAReq
      }
    }
    is(initReg) {
      for (i <- 0 until 8) {
        reg(i) := io.reg_in.reginfo(i)
      }
      state_r := initDone
    }
    is(initDone) {
      state_r := idle
    }

/////////////////////////////////////////////////////////////////////////////////////////
    is(ldAReq) {
      for (i <- 0 until 8) {
        // channel A
        io.tl.hbl2_tl(i).a.a_valid       := true.B
        io.tl.hbl2_tl(i).a.a_opcode      := TLMessages.Get
        io.tl.hbl2_tl(i).a.a_param       := 0.U
        io.tl.hbl2_tl(i).a.a_size        := cachelineBytesLog2.U
        io.tl.hbl2_tl(i).a.a_source      := i.U
        io.tl.hbl2_tl(i).a.a_address     := i.U * 64.U // TODO: reverse the address in ld after st
        io.tl.hbl2_tl(i).a.a_user_matrix := i.U
        io.tl.hbl2_tl(i).a.a_mask        := 0.U
        io.tl.hbl2_tl(i).a.a_data        := 0.U
        io.tl.hbl2_tl(i).a.a_corrupt     := 0.U
      }

      // TODO: when all channel A are ready, go to next state
      state_r := ldMData
    }

    is(ldMData) {
      for (i <- 0 until 8) {
        // channel M: store first 32B to register
        when(io.tl.hbl2_tl(i).m.m_valid) {
          reg(i) := io.tl.hbl2_tl(i).m.m_data(aPutBits-1, 0)
        }
      }
      state_r := ldDone
    }

    is(ldDone) {
      state_r := idle
    }

/////////////////////////////////////////////////////////////////////////////////////////
    is(stAReq) {
      for (i <- 0 until 8) {
        // channel A
        io.tl.hbl2_tl(i).a.a_valid       := true.B
        io.tl.hbl2_tl(i).a.a_opcode      := TLMessages.PutFullData
        io.tl.hbl2_tl(i).a.a_param       := 0.U
        io.tl.hbl2_tl(i).a.a_size        := cachelineBytesLog2.U
        io.tl.hbl2_tl(i).a.a_source      := i.U
        io.tl.hbl2_tl(i).a.a_address     := i.U * 64.U 
        io.tl.hbl2_tl(i).a.a_user_matrix := i.U
        io.tl.hbl2_tl(i).a.a_mask        := 0.U
        io.tl.hbl2_tl(i).a.a_data        := reg(i)
        io.tl.hbl2_tl(i).a.a_corrupt     := 0.U
      }
      state_r := stDAck
    }

    is(stDAck) {
      val stAllAck = WireDefault(true.B)
      for (i <- 0 until 8) {
        // channel D
        io.tl.hbl2_tl(i).d.d_ready := true.B
        when(io.tl.hbl2_tl(i).d.d_valid) {
          stAllAck := stAllAck && io.tl.hbl2_tl(i).d.d_opcode === TLMessages.AccessAckData
        }
      }
      when(stAllAck) {
        state_r := stDone
      }
    }

    is(stDone) {
      state_r := idle
    }
  }











}


