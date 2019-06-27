package org.alephium.flow.network.coordinator

import akka.io.Tcp
import akka.testkit.{SocketUtil, TestProbe}
import org.alephium.flow.PlatformConfig
import org.alephium.protocol.model.ModelGen
import org.alephium.util.AlephiumActorSpec

class BrokerSpec extends AlephiumActorSpec("BrokerSpec") {
  it should "follow this workflow" in new PlatformConfig.Default {
    val connection = TestProbe()
    val broker     = system.actorOf(Broker.props())
    watch(broker)

    broker.tell(Tcp.Connected(SocketUtil.temporaryServerAddress(), config.publicAddress),
                connection.ref)
    connection.expectMsgType[Tcp.Register]
    connection.expectMsgType[Tcp.Write]

    val randomInfo = ModelGen.cliqueInfo.sample.get
    val infoData   = BrokerConnector.envolop(randomInfo).data
    broker.tell(Tcp.Received(infoData), connection.ref)
    connection.expectMsgType[Tcp.Write]

    val ready = BrokerConnector.envolop(CliqueCoordinator.Ready).data
    broker.tell(Tcp.Received(ready), connection.ref)
    connection.expectMsg(Tcp.ConfirmedClose)

    broker ! Tcp.ConfirmedClosed
    expectTerminated(broker)
  }
}