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
import utility.TLLogger.a


class AMUCore_IO (implicit p: Parameters, params: TLBundleParameters) extends AMUBundle {
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

class AMUCore (implicit p: Parameters, params: TLBundleParameters)  extends Module with AMUParameter {
  val io = IO(new AMUCore_IO)

  // 8 * 32B vector
  val reg = Reg(Vec(8, new DSBlock)) // Ensure DSBlock is instantiated without parameters
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
        io.tl.hbl2_tl(i).a.valid                  := true.B
        io.tl.hbl2_tl(i).a.bits.opcode            := TLMessages.Get
        io.tl.hbl2_tl(i).a.bits.param             := 0.U
        io.tl.hbl2_tl(i).a.bits.size              := cachelineBytesLog2.U
        io.tl.hbl2_tl(i).a.bits.source            := i.U
        io.tl.hbl2_tl(i).a.bits.address           := i.U * 64.U // TODO: reverse the address in ld after st
        // io.tl.hbl2_tl(i).a.bits.user(MatrixKey)   := 1.U
        io.tl.hbl2_tl(i).a.bits.mask              := 0.U
        io.tl.hbl2_tl(i).a.bits.data              := 0.U
        io.tl.hbl2_tl(i).a.bits.corrupt           := 0.U
      }

      // TODO: when all channel A are ready, go to next state
      state_r := ldMData
    }

    is(ldMData) {
      for (i <- 0 until 8) {
        // channel M: store first 32B to register
        when(io.tl.hbl2_tl(i).m.valid) {
          reg(i) := io.tl.hbl2_tl(i).m.bits.data
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
        io.tl.hbl2_tl(i).a.valid                  := true.B
        io.tl.hbl2_tl(i).a.bits.opcode            := TLMessages.PutFullData
        io.tl.hbl2_tl(i).a.bits.param             := 0.U
        io.tl.hbl2_tl(i).a.bits.size              := cachelineBytesLog2.U
        io.tl.hbl2_tl(i).a.bits.source            := i.U
        io.tl.hbl2_tl(i).a.bits.address           := i.U * 64.U 
        // io.tl.hbl2_tl(i).a.bits.user(MatrixKey)   := 1.U
        io.tl.hbl2_tl(i).a.bits.mask              := 0.U
        io.tl.hbl2_tl(i).a.bits.data              := reg(i).data(aPutBits-1, 0)
        io.tl.hbl2_tl(i).a.bits.corrupt           := 0.U
      }
      state_r := stDAck
    }

    is(stDAck) {
      val stAllAck = WireDefault(true.B)
      for (i <- 0 until 8) {
        // channel D
        io.tl.hbl2_tl(i).d.ready := true.B
        when(io.tl.hbl2_tl(i).d.valid) {
          stAllAck := stAllAck && io.tl.hbl2_tl(i).d.bits.opcode === TLMessages.AccessAckData
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


