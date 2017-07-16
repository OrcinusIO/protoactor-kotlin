package actor.proto.remote

import actor.proto.*
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver

class EndpointWriter(private val address: String) : Actor {
    private var serializerId: Int = 0
    private lateinit var channel: ManagedChannel
    private lateinit var client: RemotingGrpc.RemotingStub
    private lateinit var streamWriter: StreamObserver<RemoteProtos.MessageBatch>
    suspend override fun receiveAsync (context : Context) {
        val tmp = context.message
        when (tmp) {
            is Started -> startedAsync()
            is Stopped -> stoppedAsync()
            is Restarting -> restartingAsync()
            is MutableList<*> -> {
                @Suppress("UNCHECKED_CAST")
                val m = tmp as MutableList<RemoteDeliver>
                val envelopes : MutableList<RemoteProtos.MessageEnvelope> = mutableListOf()
                val typeNames : HashMap<String, Int> = HashMap()
                val targetNames : HashMap<String, Int> = HashMap()
                val typeNameList : MutableList<String> = mutableListOf()
                val targetNameList : MutableList<String> = mutableListOf()
                for((message, target, sender, explicitSerializerId) in m) {
                    val targetName : String = target.id
                    val serializerId : Int = if (explicitSerializerId == -1) serializerId else explicitSerializerId

                    val targetId = targetNames.getOrPut(targetName){
                        targetNameList.add(targetName)
                        targetNames.count()
                    }
                    val typeName : String = Serialization.getTypeName(message, serializerId)

                    val typeId = typeNames.getOrPut(typeName){
                        typeNameList.add(typeName)
                        typeNames.count()
                    }

                    val bytes : ByteString = Serialization.serialize(message, serializerId)
                    val envelope = MessageEnvelope(bytes, sender, targetId, typeId, serializerId)
                    envelopes.add(envelope)
                }
                val batchBuilder  = RemoteProtos.MessageBatch.newBuilder()
                batchBuilder.targetNamesList.addAll(targetNameList)
                batchBuilder.typeNamesList.addAll(typeNameList)
                batchBuilder.envelopesList.addAll(envelopes)
                val batch = batchBuilder.build()
                sendEnvelopesAsync(batch, context)
            }
        }
    }

    private suspend fun sendEnvelopesAsync (batch : RemoteProtos.MessageBatch, context : Context) {
        try {
            streamWriter.onNext(batch)
        } catch (x: Exception) {
            context.stash()
            println("gRPC Failed to send to address $address, reason ${x.message}")
            throw  x
        }
    }
    private suspend fun restartingAsync () = channel.shutdownNow()
    private suspend fun stoppedAsync () = channel.shutdownNow()
    private suspend fun startedAsync () {
        println("Connecting to address $address")
        val parts = address.split(':')
        val host = parts[0]
        val port = parts[1].toInt()
        channel =  ManagedChannelBuilder.forAddress(host,port).build()
        client =  RemotingGrpc.newStub(channel)
        val blockingClient = RemotingGrpc.newBlockingStub(channel)
        val res =blockingClient.connect(connectRequest())
        serializerId = res.defaultSerializerId
        streamWriter = client.receive(null)
//        launch(CommonPool){
//            try  {
//                stream.responseStream.forEachAsync{
//
//                }
//            }
//            catch (x : Exception) {
//                println("Lost connection to address $address, reason ${x.message}")
//                val terminated : EndpointTerminatedEvent = EndpointTerminatedEvent(address)
//                EventStream.publish(terminated)
//            }
//        }

        println("Connected to address $address")
    }
}
