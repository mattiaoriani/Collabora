package org.gammf.collabora.database.actors.worker

import akka.actor.{ActorRef, Props, Stash}
import org.gammf.collabora.util.{Collaboration, Module, Note, UpdateMessage, UpdateMessageTarget, UpdateMessageType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import org.gammf.collabora.database._
import org.gammf.collabora.database.messages.{AskConnectionMessage, ChangeModuleState, GetConnectionMessage}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.gammf.collabora.yellowpages.util.Topic
import org.gammf.collabora.yellowpages.TopicElement._
import org.gammf.collabora.yellowpages.ActorService._
import org.gammf.collabora.yellowpages.messages.RegistrationResponseMessage
import org.gammf.collabora.yellowpages.util.Topic.ActorTopic
import org.gammf.collabora.yellowpages.util.Topic._
import org.gammf.collabora.yellowpages.util.Topic
import org.gammf.collabora.yellowpages.TopicElement._
import org.gammf.collabora.yellowpages.ActorService._

/**
  * A Worker that performs module state changement. The changement is based on previous module state and
  * the state of the notes
  */
class DBWorkerChangeModuleStateActor(override val yellowPages: ActorRef,
                                     override val name: String,
                                     override val topic: ActorTopic,
                                     override val service: ActorService = StateChanger)
  extends CollaborationsDBWorker[Option[BSONDocument]] with Stash {

  override def receive: Receive = ({
    //TODO consider: these three methods in super class?
    case message: RegistrationResponseMessage => getActorOrElse(Topic() :+ Database, ConnectionHandler, message)
      .foreach(_ ! AskConnectionMessage())

    case message: GetConnectionMessage =>
      connection = Some(message.connection)
      unstashAll()

    case _ if connection.isEmpty => stash()

    case ChangeModuleState(collaborationId, moduleId) => handleModuleChangeState(collaborationId, moduleId)

  }: Receive) orElse super[CollaborationsDBWorker].receive

  private[this] def handleModuleChangeState(collaborationId: String, moduleId: String): Unit = {
    getModule(moduleId) map {
      case Some(module) => getModulesNotes(collaborationId, moduleId).map(list =>
        if (list.nonEmpty) {
          val noteListSize: Int = list.size
          getActorOrElse(Topic() :+ Database, Master, ChangeModuleState(collaborationId, moduleId))
            .foreach(_ ! (noteListSize match {
              case _ if list.count(note => note.state.definition == State.TODO) == noteListSize &&
                module.state != State.TODO =>
                buildUpdateMessage(
                  collaborationId = collaborationId,
                  updatedModule = Module(id = module.id, description = module.description, state = State.TODO)
                )
              case _ if list.count(note => note.state.definition == State.DONE) == noteListSize &&
                module.state != State.DONE =>
                buildUpdateMessage(
                  collaborationId = collaborationId,
                  updatedModule = Module(id = module.id, description = module.description, state = State.DONE)
                )
              case _ if list.count(note => note.state.definition == State.TODO) != noteListSize &&
                list.count(note => note.state.definition == State.DONE) != noteListSize &&
                module.state != State.DOING =>
                buildUpdateMessage(
                  collaborationId = collaborationId,
                  updatedModule = Module(id = module.id, description = module.description, state = State.DOING)
                )
              case _ => None
            }))
        }
      )
      case None => // do nothing
    }
  }

  /**
    * Gets a list of notes, contained in the given module.
    * @param collaborationId the id of the collaboration
    * @param moduleId the id of the module
    * @return a list of notes contained in the given module. If no note is contained in the given module
    *         returns an empty list
    */
  private[this] def getModulesNotes(collaborationId: String, moduleId: String): Future[List[Note]] = {
    find(
      selector = BSONDocument(COLLABORATION_ID -> BSONObjectID.parse(collaborationId).get),
      okStrategy = bsonDocument => bsonDocument,
      failStrategy = { case _: Exception => None}
    ).map(bson => if (bson.isDefined && bson.get.as[Collaboration].notes.isDefined)
                    bson.get.as[Collaboration].notes.get.filter(note => note.module.isDefined && note.module.get == moduleId)
                  else List()
    )
  }

  /**
    * Get a module
    * @param moduleId the module ID
    * @return a [[Future]] representing the result. If the module exists Some(module) is returned. If the
    *         module not exists an empty Option is returned.
    */
  private[this] def getModule(moduleId: String): Future[Option[Module]] = {
    find(
      selector = BSONDocument(COLLABORATION_MODULES + "." + MODULE_ID -> BSONObjectID.parse(moduleId).get),
      okStrategy = bsonDocument => bsonDocument,
      failStrategy = { case _: Exception => None}
    ).map(bson => if (bson.isDefined && bson.get.as[Collaboration].modules.isDefined)
                      bson.get.as[Collaboration].modules.get.find(module => module.id.get == moduleId)
                  else None
    )
  }

  private[this] def buildUpdateMessage(collaborationId: String, updatedModule: Module): UpdateMessage = {
    UpdateMessage(
      target = UpdateMessageTarget.MODULE,
      messageType = UpdateMessageType.UPDATING,
      user = "Collabora server",
      module = Some(updatedModule),
      collaborationId = Some(collaborationId)
    )
  }
}

private object State {
  val TODO: String = "To Do"
  val DOING: String = "Doing"
  val DONE: String = "Done"
}

object DBWorkerChangeModuleStateActor {

  /**
    * Factory methods that return a [[Props]] to create a database worker change module state registered actor
    * @param yellowPages the reference to the yellow pages root actor.
    * @param topic the topic to which this actor is going to be registered.
    * @return the [[Props]] to use to create a database worker change module state actor.
    */
  def printerProps(yellowPages: ActorRef, topic: ActorTopic, name: String = "DBWorkerChangeModuleState") : Props =
    Props(new DBWorkerChangeModuleStateActor(yellowPages = yellowPages, name = name, topic = topic))
}