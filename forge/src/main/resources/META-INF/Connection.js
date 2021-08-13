var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
var ASMAPI = Java.type("net.minecraftforge.coremod.api.ASMAPI");

function transformMethod(method) {
    var instructions = method.instructions;
    instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/shedaniel/lightoverlay/forge/mixin/MixinClientConnection", "handlePacket", "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V", false));
    instructions.insert(new VarInsnNode(Opcodes.ALOAD, 1));
    instructions.insert(new VarInsnNode(Opcodes.ALOAD, 0));
}

function initializeCoreMod() {
    return {
        "lightoverlay": {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.network.Connection'
            },
            'transformer': function (classNode) {
                var genericsFtw = ASMAPI.mapMethod("m_129517_");
                for (i in classNode.methods) {
                    var method = classNode.methods[i];
                    if (method.name === genericsFtw) {
                        transformMethod(method)
                        break;
                    }
                }
                return classNode;
            }
        }
    }
}