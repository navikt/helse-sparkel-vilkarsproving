package no.nav.helse.sparkel.component

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.*

class ComponentTest {

    companion object {

        val kafka = KafkaContainer("4.1.2").withExposedPorts(2181, 9093)

        @BeforeAll
        @JvmStatic
        fun setup() {
            kafka.start()
            val createTopicCmd = "/usr/bin/kafka-topics --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic privat-helse-sykepenger-behov"
            val result = kafka.execInContainer("/bin/sh", "-c", createTopicCmd)
            println(result.stdout)

        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            kafka.stop()
        }
    }

    @Test
    fun testStuff() {
        writeToTopic("""{"aaa": 123}""")
        assertTrue(true)
    }

    private fun writeToTopic(msg: String) {
        val sendMsgCmd = """echo -n "$msg" | /usr/bin/kafka-console-producer --topic privat-helse-sykepenger-behov --broker-list localhost:9092"""
        val result = kafka.execInContainer("/bin/sh", "-c", sendMsgCmd)
        println(if (result.exitCode == 0) "wrote $msg" else "error: ${result.stderr}")
    }

}