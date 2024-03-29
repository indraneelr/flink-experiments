package com.example

import com.example.sink.Writer
import com.example.source.StreamProvider
import com.example.windowing.{SessionCountFunction, SessionProcessFunction}
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend
import org.apache.flink.runtime.state.filesystem.FsStateBackend
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.ContinuousEventTimeTrigger

object App {

  lazy val defaultList: Seq[Event] = Seq(
    Event("u1", "e1", 1L), //s1
    Event("u2", "e2", 1L),
    Event("u1", "e3", 3L), //s1
    Event("u2", "e4", 5L),
    Event("u1", "e5", 6L), //s1
    Event("u2", "e6", 10L),
    Event("u1", "e7", 11L), //s2
    Event("u1", "e8", 12L), //s2
    Event("u1", "e9", 16L), //s2
    Event("u2", "e10", 13L),
    Event("u1", "e11", 14L), //s2
    Event("u1", "e12", 7L), // late event discarded , gotcha!
    Event("u1", "e13", 20L), // late event discarded , gotcha!

  )

  def main(args: Array[String]): Unit = {
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(2)

    setupStateManagement(env)


    val source: DataStream[Event] = StreamProvider.fromFile("/user-events-1.csv", env)
    //    val source: DataStream[Event] = StreamProvider.fromList(defaultList,env)

    val tag: OutputTag[Event] = OutputTag("late-data")
    // We create sessions for each id with max timeout of 3 time units
    val sessionizedStream: DataStream[EnrichedEvent] = source
      .keyBy(item => item.userId)
      .window(EventTimeSessionWindows.withGap(Time.milliseconds(3L)))
      .sideOutputLateData(tag)
      .allowedLateness(Time.milliseconds(4))
      .process(new SessionProcessFunction)


    //session aggregation
    val aggregatedStream = sessionizedStream
      .keyBy(event => event.userId)
      .timeWindow(Time.seconds(1))
      .process(new SessionCountFunction)

    val writer = new Writer(env)

    writer.toConsole(sessionizedStream)
    writer.toConsole(sessionizedStream.getSideOutput(tag))
    writer.toConsole(aggregatedStream)

    env.execute()
  }

  private def setupStateManagement(env: StreamExecutionEnvironment) = {
    val checkpointLocation = "file:///tmp/flink-checkpoints"
    val rocksDBStateBackend = new RocksDBStateBackend(checkpointLocation, true)
    rocksDBStateBackend.setDbStoragePath("file:///tmp/rocksdb-files")
    env.setStateBackend(rocksDBStateBackend)

//    val fsStateBackend = new FsStateBackend(checkpointLocation, true)
//    env.setStateBackend(fsStateBackend)

    println(env.getStateBackend)
  }
}
