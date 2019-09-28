package com.example

case class EnrichedEvent(
                          userId: String,
                          eventId: String,
                          timestamp: Long,
                          sessionId: String = "",
                          count: Int = 1,
                          channel:String = "",
                          attributedChannel:String = "",
                          processingTime: String)
