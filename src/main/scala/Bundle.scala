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

trait AMUParameter {
  val pAddrBits: Int = 64 // address bits

  // Channel A: ST 32B
  val aPutBits: Int = 256 // 32B
  val aPutBytes: Int = aPutBits / 8 

  // Channel M: LD 64B
  val mGetBits: Int = 512 // 64B
  val mGetBytes: Int = mGetBits / 8

  // Channel D: data not used
  val dAckBits: Int = 256 // 32B
  val dAckBytes: Int = dAckBits / 8

  val cachelineBytesLog2: Int = 6 // 64B
}

class AMUBundle extends Bundle with AMUParameter

/////////////////////////////////////////////////////////////////////////////////////////////////////////
class TL_A extends AMUBundle {
  val a_valid       = Output(Bool())             // valid
  val a_ready       = Input(Bool())             // ready
  val a_opcode      = Output(UInt(4.W))          // PUT/GET
  val a_param       = Output(UInt(3.W))          /* not used */
  val a_size        = Output(UInt(3.W))          // 2^a_size bytes
  val a_source      = Output(UInt(5.W))  
  val a_address     = Output(UInt(pAddrBits.W)) 
  val a_user_matrix = Output(UInt(2.W))    
  val a_mask        = Output(UInt(32.W))         /* not used */
  val a_data        = Output(UInt(aPutBits.W))   // only for PUT request
  val a_corrupt     = Output(UInt(1.W))          /* not used */
}

class TL_D extends AMUBundle {
  val d_valid       = Input(Bool())             // valid
  val d_ready       = Output(Bool())             // ready
  val d_opcode      = Input(UInt(4.W))          // ACK
  val d_param       = Input(UInt(3.W))          /* not used */
  val d_size        = Input(UInt(3.W))          /* not used */
  val d_source      = Input(UInt(5.W))          // compare with a_source
  val d_sink        = Input(UInt(11.W))         /* not used */
  val d_denied      = Input(UInt(1.W))          /* not used */
  val d_data        = Input(UInt(dAckBits.W))   /* not used !!! */
  val d_corrupt     = Input(UInt(1.W))          /* not used */
}

class TL_M extends AMUBundle {
  val m_valid       = Input(Bool())             // valid
  val m_source      = Input(UInt(5.W))  
  val m_data        = Input(UInt(mGetBits.W))   // only for GET request
}

class TL_Link extends AMUBundle {
  val a = (new TL_A)
  val d = (new TL_D)
  val m = (new TL_M)
}

class HBL2_TL extends AMUBundle {
  val hbl2_tl = Vec(8, new TL_Link) // 8 banks, 8 links
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////
class RegInfo extends AMUBundle {
  val reginfo = (Vec(8, UInt(mGetBits.W))) // 8 banks
}
