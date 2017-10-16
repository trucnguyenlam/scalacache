package scalacache.caffeine

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}

import com.github.benmanes.caffeine.cache.{Caffeine, Cache => CCache}
import org.slf4j.LoggerFactory

import scalacache.{AbstractCache, CacheConfig, Entry, Flags, LoggingSupport, Mode, Sync}
import scala.concurrent.duration.Duration
import scala.language.higherKinds

/*
 * Thin wrapper around Caffeine.
 *
 * This cache implementation is synchronous.
 */
class CaffeineCache[V](underlying: CCache[String, Entry[V]])(implicit val config: CacheConfig,
                                                             clock: Clock = Clock.systemUTC())
    extends AbstractCache[V, Sync]
    with LoggingSupport {

  override protected final val logger =
    LoggerFactory.getLogger(getClass.getName)

  def doGet[F[_]](key: String)(implicit mode: Mode[F, Sync], flags: Flags): F[Option[V]] = {
    mode.M.delay {
      val baseValue = underlying.getIfPresent(key)
      val result = {
        if (baseValue != null) {
          val entry = baseValue.asInstanceOf[Entry[V]]
          if (entry.isExpired) None else Some(entry.value)
        } else None
      }
      logCacheHitOrMiss(key, result)
      result
    }
  }

  def doPut[F[_]](key: String, value: V, ttl: Option[Duration])(implicit mode: Mode[F, Sync], flags: Flags): F[Any] = {
    mode.M.delay {
      val entry = Entry(value, ttl.map(toExpiryTime))
      underlying.put(key, entry)
      logCachePut(key, ttl)
    }
  }

  override def doRemove[F[_]](key: String)(implicit mode: Mode[F, Sync]): F[Any] =
    mode.M.delay(underlying.invalidate(key))

  override def doRemoveAll[F[_]]()(implicit mode: Mode[F, Sync]): F[Any] =
    mode.M.delay(underlying.invalidateAll())

  def close(): Unit = {
    // Nothing to do
  }

  private def toExpiryTime(ttl: Duration): Instant =
    Instant.now(clock).plus(ttl.toMillis, ChronoUnit.MILLIS)

}

object CaffeineCache {

  /**
    * Create a new Caffeine cache
    */
  def apply[V](implicit config: CacheConfig): CaffeineCache[V] =
    apply(Caffeine.newBuilder().build[String, Entry[V]]())

  /**
    * Create a new cache utilizing the given underlying Caffeine cache.
    *
    * @param underlying a Caffeine cache
    */
  def apply[V](underlying: CCache[String, Entry[V]])(implicit config: CacheConfig): CaffeineCache[V] =
    new CaffeineCache(underlying)

}
