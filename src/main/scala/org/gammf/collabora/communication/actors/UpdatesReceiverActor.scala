package org.gammf.collabora.communication.actors

import akka.actor._
import org.gammf.collabora.communication.Utils.CommunicationType
import org.gammf.collabora.communication.messages._
import org.gammf.collabora.util.UpdateMessage
import org.gammf.collabora.yellowpages.ActorService.ActorService
import org.gammf.collabora.yellowpages.actors.{BasicActor, YellowPagesActor}
import play.api.libs.json.{JsError, JsSuccess, Json}
import org.gammf.collabora.yellowpages.messages._
import org.gammf.collabora.yellowpages.util.Topic._
import org.gammf.collabora.yellowpages.util.Topic
import org.gammf.collabora.yellowpages.TopicElement._
import org.gammf.collabora.yellowpages.ActorService._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * @author Manuel Peruzzi
  */

/**
  * This is an actor that manages the reception of client updates.
  * @param connection the open connection with the rabbitMQ broker.
  * @param naming the reference to a rabbitMQ naming actor.
  * @param channelCreator the reference to a channel creator actor.
  * @param subscriber the reference to a subscriber actor.
  */
class UpdatesReceiverActor(override val yellowPages: ActorRef, override val name: String,
                           override val topic: ActorTopic, override val service: ActorService) extends BasicActor {

  private[this] var subQueue: Option[String] = None

  override def receive: Receive = ({
    case message: RegistrationResponseMessage => getActorOrElse(Topic() :+ Communication :+ RabbitMQ, Naming, message).foreach(actorRef => actorRef ! ChannelNamesRequestMessage(CommunicationType.UPDATES))
    case message: ChannelNamesResponseMessage =>
      subQueue = message.queue
      getActorOrElse(Topic() :+ Communication :+ RabbitMQ, ChannelCreating, message).foreach(_ ! SubscribingChannelCreationMessage(message.exchange, subQueue.get, None))
    case message: ChannelCreatedMessage => getActorOrElse(Topic() :+ Communication :+ RabbitMQ, Subscribing, message).foreach(_ ! SubscribeMessage(message.channel, subQueue.get))
    case message: ClientUpdateMessage =>
      Json.parse(message.text).validate[UpdateMessage] match {
        case updateMessage: JsSuccess[UpdateMessage] => getActorOrElse(Topic() :+ Database, Master, message).foreach(_ ! updateMessage.value)
        case error: JsError => println(error)
      }
  }: Receive) orElse super[BasicActor].receive
}

object Test extends App {
  val topic = Topic() :+ Communication :+ RabbitMQ
  val service = Naming

  val system = ActorSystem("CollaboraServer")
  val root = system.actorOf(YellowPagesActor.rootProps())

  val updatesReceiver = system.actorOf(Props(
    new UpdatesReceiverActor(root,"Update Receiver", topic, UpdatesReceiving)), "updates-receiver")

  Thread.sleep(5000)

  val naming = system.actorOf(Props(new RabbitMQNamingActor(root, "Naming Actor", topic, service)), "naming")

  //system.terminate()
}
