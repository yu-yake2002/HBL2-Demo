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


class AMU(implicit p: Parameters, params: TLBundleParameters) extends LazyModule {

  // 8 client node
  val matrix_nodes = (0 until 1).flatMap { i =>
    (0 until 8).map { j =>
      TLClientNode(Seq(
        TLMasterPortParameters.v1(
          clients = Seq(TLMasterParameters.v1(
            name = s"matrix${i}_${j}",
            sourceId = IdRange(0, 32)
          )),
          requestFields = Seq(MatrixField(2))
        )
      ))
    }
  }

  lazy val module: AMUImp = new AMUImp(this, params) 
}

