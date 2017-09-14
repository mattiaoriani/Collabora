package org.gammf.collabora.database.actors

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.newmotion.akka.rabbitmq.{ConnectionActor, ConnectionFactory}
import org.gammf.collabora.TestUtil
import org.gammf.collabora.communication.actors._
import org.gammf.collabora.database.actors.master.DBMasterActor
import org.gammf.collabora.database.actors.worker.DBWorkerCollaborationsActor
import org.gammf.collabora.database.messages._
import org.gammf.collabora.util.{CollaborationRight, CollaborationType, CollaborationUser, Location, Module, NoteState, SimpleCollaboration, SimpleModule, SimpleNote}
import org.gammf.collabora.yellowpages.ActorCreator
import org.gammf.collabora.yellowpages.ActorService.ConnectionHandler
import org.gammf.collabora.yellowpages.actors.YellowPagesActor
import org.gammf.collabora.yellowpages.messages.{RegistrationRequestMessage, RegistrationResponseMessage}
import org.gammf.collabora.yellowpages.util.Topic
import org.gammf.collabora.yellowpages.TopicElement._
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class DBWorkerCollaborationsActorTest extends TestKit (ActorSystem("CollaboraServer")) with WordSpecLike  with Matchers with BeforeAndAfterAll with ImplicitSender {

  val COLLABORATION_ID:String = "123456788698540008123400"
  val COLLABORATION_NAME = "simplecollaboration"
  val COLLABORATION_USER_FONE = "fone"
  val COLLABORATION_USER_PERU = "peru"
  val COLLABORATION_FIRST_NOTE_CONTENT = "questo è il contenuto"
  val COLLABORATION_FIRST_LOCATION_LATITUDE = 23.32
  val COLLABORATION_FIRST_LOCATION_LONGITUDE = 23.42
  val COLLABORATION_SECOND_LOCATION_LATITUDE = 233.32
  val COLLABORATION_SECOND_LOCATION_LONGITUDE = 233.42
  val COLLABORATION_SECOND_NOTE_CONTENT = "questo è il contenuto2"
  val COLLABORATION_STATE_DOING = "doing"
  val COLLABORATION_STATE_DONE = "done"

  val CONNECTION_ACTOR_NAME = "RabbitConnection"
  val MONGO_CONNECTION_ACTOR_NAME = "MongoConnectionManager"
  val DBMASTER_ACTOR_NAME = "DBMaster"
  val DBWORKER_COLLAB_ACTOR_NAME = "DBWorkerCollaborations"

  val actorCreator = new ActorCreator(system)
  val rootYellowPages = actorCreator.getYellowPagesRoot

  val factory = new ConnectionFactory()
  val rabbitConnection = system.actorOf(ConnectionActor.props(factory), CONNECTION_ACTOR_NAME)
  rootYellowPages ! RegistrationRequestMessage(rabbitConnection, CONNECTION_ACTOR_NAME, Topic() :+ Communication :+ RabbitMQ, ConnectionHandler)

  val mongoConnectionActor = system.actorOf(ConnectionManagerActor.printerProps(rootYellowPages, Topic() :+ Database, MONGO_CONNECTION_ACTOR_NAME))
  val dbMasterActor = system.actorOf(DBMasterActor.printerProps(rootYellowPages, Topic() :+ Database, DBMASTER_ACTOR_NAME))
  val dBWorkerCollaborationsActor = system.actorOf(DBWorkerCollaborationsActor.printerProps(rootYellowPages, Topic() :+ Database :+ Collaboration))

  val collab = SimpleCollaboration(
    id = Some(COLLABORATION_ID),
    name = COLLABORATION_NAME,
    collaborationType = CollaborationType.GROUP,
    users = Some(List(CollaborationUser(COLLABORATION_USER_FONE, CollaborationRight.ADMIN), CollaborationUser(COLLABORATION_USER_PERU, CollaborationRight.ADMIN))),
    modules = Option.empty,
    notes = Some(List(SimpleNote(None, COLLABORATION_FIRST_NOTE_CONTENT,Some(new DateTime()),Some(Location(COLLABORATION_FIRST_LOCATION_LATITUDE,COLLABORATION_FIRST_LOCATION_LONGITUDE)),Option.empty,NoteState(COLLABORATION_STATE_DOING, Some(COLLABORATION_USER_PERU)),None),
      SimpleNote(None,COLLABORATION_SECOND_NOTE_CONTENT,Some(new DateTime()),Some(Location(COLLABORATION_SECOND_LOCATION_LATITUDE,COLLABORATION_SECOND_LOCATION_LONGITUDE)),None,NoteState(COLLABORATION_STATE_DONE, Option(COLLABORATION_USER_PERU)),None)))
  )

  override def beforeAll(): Unit = {

  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A DBWorkerCollaborations actor" should {
    "insert new collaboration in the db" in {

      within(TestUtil.TASK_WAIT_TIME second) {
        dBWorkerCollaborationsActor ! InsertCollaborationMessage(collab, TestUtil.USER_ID)
        expectMsg(RegistrationResponseMessage())
      }
    }

    "update a collaboration in the db" in {
      within(TestUtil.TASK_WAIT_TIME second) {
        dBWorkerCollaborationsActor ! UpdateCollaborationMessage(collab, TestUtil.USER_ID)
        expectMsgType[QueryOkMessage]
      }
    }

    "delete a collaboration in the db" in {
      within(TestUtil.TASK_WAIT_TIME second) {
        dBWorkerCollaborationsActor ! DeleteCollaborationMessage(collab, TestUtil.USER_ID)
        expectMsgType[QueryOkMessage]
      }
    }
  }
}
