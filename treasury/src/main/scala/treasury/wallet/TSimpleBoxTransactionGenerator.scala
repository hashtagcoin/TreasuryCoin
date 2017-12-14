package treasury.wallet

import akka.actor.{Actor, ActorRef}
import examples.commons.{SimpleBoxTransaction, SimpleBoxTransactionMemPool}
import examples.curvepos.Value
import examples.hybrid.wallet.HWallet
import scorex.core.LocalInterface.LocallyGeneratedTransaction
import scorex.core.NodeViewHolder.{CurrentView, GetDataFromCurrentView}
import scorex.core.transaction.box.proposition.PublicKey25519Proposition
import scorex.core.utils.ScorexLogging
import treasury.history.TreasuryHistory
import treasury.state.TBoxStoredState

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Random, Success, Try}

/**
  * Generator of SimpleBoxTransaction inside a wallet
  */
class TSimpleBoxTransactionGenerator(viewHolderRef: ActorRef) extends Actor with ScorexLogging {

  import SimpleBoxTransactionGenerator._

  private val getRequiredData: GetDataFromCurrentView[TreasuryHistory,
    TBoxStoredState,
    TWallet,
    SimpleBoxTransactionMemPool,
    GeneratorInfo] = {
    val f: CurrentView[TreasuryHistory, TBoxStoredState, TWallet, SimpleBoxTransactionMemPool] => GeneratorInfo = {
      view: CurrentView[TreasuryHistory, TBoxStoredState, TWallet, SimpleBoxTransactionMemPool] =>
        GeneratorInfo(generate(view.vault))
    }
    GetDataFromCurrentView[TreasuryHistory,
      TBoxStoredState,
      TWallet,
      SimpleBoxTransactionMemPool,
      GeneratorInfo](f)
  }


  override def receive: Receive = {
    case StartGeneration(duration) =>
      context.system.scheduler.schedule(duration, duration, viewHolderRef, getRequiredData)

    //    case CurrentView(_, _, wallet: TWallet, _) =>
    case gi: GeneratorInfo =>
      gi.tx match {
        case Success(tx) =>
          log.info(s"Local tx with with ${tx.from.size} inputs, ${tx.to.size} outputs. Valid: ${tx.semanticValidity}")
          viewHolderRef ! LocallyGeneratedTransaction[PublicKey25519Proposition, SimpleBoxTransaction](tx)
        case Failure(e) =>
          e.printStackTrace()
      }
  }

  private val ex: ArrayBuffer[Array[Byte]] = ArrayBuffer()

  def generate(wallet: TWallet): Try[SimpleBoxTransaction] = {
    if (Random.nextInt(100) == 1) ex.clear()

    val pubkeys = wallet.publicKeys.toSeq
    if (pubkeys.size < 10) wallet.generateNewSecret()
    val recipients = scala.util.Random.shuffle(pubkeys).take(Random.nextInt(pubkeys.size))
      .map(r => (r, Value @@ Random.nextInt(100).toLong))
    val tx = SimpleBoxTransaction.create(wallet, recipients, Random.nextInt(100), ex)
    tx.map(t => t.boxIdsToOpen.foreach(id => ex += id))
    tx
  }
}

object SimpleBoxTransactionGenerator {

  case class StartGeneration(delay: FiniteDuration)

  case class GeneratorInfo(tx: Try[SimpleBoxTransaction])

}

