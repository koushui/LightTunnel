package lighttunnel.base.proto

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class ProtoMessageEncoder : MessageToByteEncoder<ProtoMessage>() {

    @Throws(Exception::class)
    override fun encode(ctx: ChannelHandlerContext?, msg: ProtoMessage?, out: ByteBuf?) {
        msg ?: return
        out ?: return
        val totalLength = PROTO_MESSAGE_HEAD_LENGTH_FIELD_LENGTH +
            PROTO_MESSAGE_TYPE_LENGTH +
            msg.head.size +
            msg.data.size
        out.writeInt(totalLength)
        out.writeByte(msg.type.code.toInt())
        out.writeInt(msg.head.size)
        out.writeBytes(msg.head)
        out.writeBytes(msg.data)
    }
}
