package com.example

import java.text.SimpleDateFormat
import java.util.Calendar

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.{ delete, get, post }
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.util.Timeout
import com.example.UserRegistryActor._
import com.redis._

import scala.concurrent.Future
import scala.concurrent.duration._

//import akka.actor.{ ActorRef, ActorSystem }; import akka.util.Timeout; import com.redis.RedisClient; import scala.concurrent.duration._
//implicit val system = ActorSystem("redis-client"); implicit val executionContext = system.dispatcher; implicit val timeout = Timeout(5 seconds); val client = RedisClient("192.168.9.9", 6379)
//#user-routes-class
trait UserRoutes extends JsonSupport {
  //#user-routes-class
  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[UserRoutes])

  // other dependencies that UserRoutes use
  def userRegistryActor: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  //#all-routes
  //#users-get-post
  //#users-get-delete   
  lazy val userRoutes: Route =
    pathPrefix("") {
      concat(
        //#users-get-delete
        pathEnd {
          concat(
            get {
              parameter('all.as[String]) { (all) =>
                {
                  implicit val system = ActorSystem("redis-client")
                  implicit val executionContext = system.dispatcher
                  implicit val timeout = Timeout(60 seconds)

                  // Redis client setup
                  val client = RedisClient("192.168.9.9", 6379)

                  val users: Future[Users] =
                    (userRegistryActor ? GetUsers).mapTo[Users]

                  //client.get("2019-02-07").map(_.getOrElse(""))
                  //val format = new SimpleDateFormat("yyyy-MM-dd")
                  //format.format(Calendar.getInstance().getTime())

                  val json = client.get("all").map(_.getOrElse(""))

                  //val html = "<html><body><p id=\"allWatts\"></p><ul id=\"TimeWatts\"></ul><script>window.onload = function(){var xhttp = new XMLHttpRequest(); xhttp.onreadystatechange = function() {if (this.readyState == 4 && this.status == 201) {var json = JSON.parse(this.responseText); var count = Object.keys(json).length; var allWatt = 0.0; var countH = 0.1; var timeH=(new Date(json[0].t*1000)).toString().substr(16,2); var oldTimeH = timeH; var sumH =0.0; for(var item in json) {var node = document.createElement(\"li\"); var watt = Number(json[item].a)*12; if (watt > 5) { sumH+=watt; countH+=1; timeH=(new Date(json[item].t*1000)).toString().substr(16,2); if(oldTimeH != timeH){var textnode = document.createTextNode((new Date(json[item].t*1000)).toString().substr(4,20) + '  ' + watt.toFixed(2) + ' Вт ' + /*'; Hour=' + timeH + '; sumH=' + sumH + '; countH = ' + countH +*/ '; AVG Power = ' + (sumH/countH).toFixed(2)); node.appendChild(textnode); document.getElementById(\"TimeWatts\").appendChild(node); sumH = 0; countH=0.1; oldTimeH = timeH;}; allWatt+=watt; }} document.getElementById(\"allWatts\").innerHTML = (allWatt/count)/1000 + ' KWtH; ' + 'Count = ' + count + '; SumWatt = ' + allWatt }};xhttp.open(\"POST\", \"/\", true);xhttp.setRequestHeader(\"Content-type\", \"application/json\");xhttp.send(JSON.stringify({\"name\":\"\", \"age\":0, \"countryOfResidence\":\"\"}));}</script></body></html>"
                  complete((StatusCodes.OK, json.map("{\"data\":[{\"t\":0, \"a\":\"0\", \"c:\":0}," + _ + "{\"t\":0, \"a\":\"0\", \"c:\":0}]}")))
                }
              }
            },
            post {
              implicit val system = ActorSystem("redis-client")
              implicit val executionContext = system.dispatcher
              implicit val timeout = Timeout(60 seconds)

              // Redis client setup
              val client = RedisClient("192.168.9.9", 6379)
              val format = new SimpleDateFormat("yyyy-MM-dd")
              log.info("Created user [{}]: {}", format.format(Calendar.getInstance().getTime()))

              val html = client.get(format.format(Calendar.getInstance().getTime())).map(_.getOrElse(""))

              entity(as[User]) { user =>
                val userCreated: Future[ActionPerformed] =
                  (userRegistryActor ? CreateUser(user)).mapTo[ActionPerformed]

                onSuccess(userCreated) { performed =>
                  //log.info("Created user [{}]: {}", user.name, performed.description)
                  complete((StatusCodes.OK, html.map("[{\"t\":0, \"a\":\"0\",\"c:\":0}," + _ + "{\"t\":0, \"a\":\"0\",\"c:\":0}]")))
                }
              }
            }
          )
        },
        //#users-get-post
        //#users-get-delete
        path(Segment) { name =>
          concat(
            get {
              //#retrieve-user-info
              val maybeUser: Future[Option[User]] =
                (userRegistryActor ? GetUser(name)).mapTo[Option[User]]
              rejectEmptyResponse {
                complete(maybeUser)
              }
              //#retrieve-user-info
            },
            delete {
              //#users-delete-logic
              val userDeleted: Future[ActionPerformed] =
                (userRegistryActor ? DeleteUser(name)).mapTo[ActionPerformed]
              onSuccess(userDeleted) { performed =>
                log.info("Deleted user [{}]: {}", name, performed.description)
                complete((StatusCodes.OK, performed))
              }
              //#users-delete-logic
            }
          )
        }
      )
      //#users-get-delete
    }
  //#all-routes
}
