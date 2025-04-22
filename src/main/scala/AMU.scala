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

class AMUIO(implicit p: Parameters) extends Bundle {
  val matrix_data_in = Vec(8, Flipped(DecoupledIO(new MatrixDataBundle())))  // 用于输出数据
}

class AMU(implicit p: Parameters) extends LazyModule {
  // 定义8个TLClientNode节点，每个节点用于并行发起请求
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

  lazy val module: AMUImp = new AMUImp(this)
}

class AMUImp(outer: AMU) extends LazyModuleImp(outer) {
  val io: AMUIO = IO(new AMUIO)

  // 获取外部的TLClientNode节点
  private val matrix_data_in = io.matrix_data_in
  matrix_data_in.foreach { in =>
    in.ready := true.B
  }

  // 将数据从TLClientNode收集到matrix_data_out
  outer.matrix_nodes.zipWithIndex.foreach { case (matrix_node, i) =>
    // 这里处理并行发起请求的逻辑
    val (bus, edge) = matrix_node.out.head
    bus.a.bits.address := 0xff_ffff.U
    bus.a.bits.opcode := TLMessages.Get// 示例请求数据
    // bus.a.bits.opcode := TLMessages.PutFullData// 示例请求数据
    bus.a.bits.user(MatrixKey) := 1.U
    bus.a.bits.data :=0x267127.U
  }
  // ...
}