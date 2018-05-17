package com.example.consul.test

import com.ecwid.consul.v1.ConsulClient
import com.example.consul.util.Locker
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext

class LockingTest extends WordSpec with Matchers with ScalaFutures {



  implicit val ec = ExecutionContext.global
  implicit val client = new ConsulClient("localhost")
  val locker = new Locker()


  def attempt(locker: Locker, name: String, key: String) = {
    val (lock, f) = locker.lock(key, name, 2000, 10)
    f.onComplete( result => {
      lock.unlock()
      result.fold({ e =>
        println(s"$name ${e}")
      }, v => {
        println(s"${name} ${v}")
      })
    })
  }

  "Consul Client" can {

    "can distribute locks" should {

      "safely and hold them" in {

        val key ="test1"

        attempt(locker, "t1", key)
        attempt(locker, "t2", key)
        attempt(locker, "t3", key)



        val (lock, f) = locker.lock(key, "goody", 2000, 10)

        whenReady(f, timeout(Span(30, Seconds))) { s =>
          s should be(true)
          lock.unlock()
        }
      }

      "have simple usage" in {

        val (lock, f) = locker.lock("simpleUsage", "simpleLock", 2000, 10)

        whenReady(f, timeout(Span(30, Seconds))) { s =>
          s should be(true)
          lock.unlock()
        }

      }
    }
  }

}