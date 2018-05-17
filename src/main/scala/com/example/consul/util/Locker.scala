package com.example.consul.util

import java.util
import java.util.UUID

import com.ecwid.consul.v1.ConsulClient
import com.ecwid.consul.v1.agent.model.NewCheck
import com.ecwid.consul.v1.kv.model.PutParams
import com.ecwid.consul.v1.session.model.{NewSession, Session}
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.Try

// see https://insight.io/github.com/dyc87112/consul-distributed-lock/blob/master/src/main/java/com/didispace/lock/consul/BaseLock.java?line=40
class Locker(implicit client: ConsulClient, ec: ExecutionContext) {

  val log = LoggerFactory.getLogger(getClass)


  def lock(key: String, label: String) = {
    val s  = session()
    val lock = Lock(s, s"locks/$key", label)
    val f = lock.lock(2000, 10)
    (lock, f)
  }

  private def session() = {
    val id = "lock-" + UUID.randomUUID().toString
    val s = new NewSession()
    s.setName(id)
    val result = client.sessionCreate(s, null)
    result.getValue
  }

}


case class Lock(session: String, path: String, label: String)(implicit client: ConsulClient, ec: ExecutionContext){

  private val log = LoggerFactory.getLogger(getClass)

  def lock(interval: Long, maxAttempts: Long = 0): Future[Boolean] ={
    val p = Promise[Boolean]()
    doLoop(p, interval, maxAttempts)
    p.future
  }

  def unlock(): Unit ={
    Future {
      val params = new PutParams
      params.setReleaseSession(session)
      val result = client.setKVValue(path, session, params)
      result.getValue
    }
  }

  def doLoop(p: Promise[Boolean], interval: Long, maxAttempts: Long = 0) = {

    Future {
      // delay running, to simulate contention
      Thread.sleep(5000)

      // track our usage and if we should continue
      var attempt = 0
      var canAttemptAgain = true

      while(canAttemptAgain) {
        // do we need to continue executing on failure?
        attempt = attempt + 1
        canAttemptAgain = attempt < maxAttempts || maxAttempts <= 0

        Try[Boolean] {
          // actually attempt the lock
          log.info(s"[$label] attempt $attempt")
          Await.result(Future[Boolean] {
            val params = new PutParams
            params.setAcquireSession(session)
            val result = client.setKVValue(path, session, params)
            result.getValue
          }, interval.seconds)
        }.toEither match {
          case Left(e) => {
            if (canAttemptAgain) {
              log.info(s"[$label] got an error from consul, trying again")
            } else {
              log.info(s"[$label] got an error from consul, failing")
              p.failure(e)
            }
            p
          }
          case Right(succeeded) => {
            if( succeeded ) {
              log.info(s"[$label] SUCCESS")
              p.success(succeeded)
            }else if (canAttemptAgain) {
              log.info(s"[$label] got a false from consul, trying again")
            } else {
              log.info(s"[$label] got a false from consul, failing")
              p.failure(new RuntimeException(s"[$label] Consul returned false after ${attempt} attempts"))
            }
            p
          }
        }



      } // end loop
    }
  }

}
