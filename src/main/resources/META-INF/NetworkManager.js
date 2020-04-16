var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var FieldNode = Java.type("org.objectweb.asm.tree.FieldNode");
var InsnNode = Java.type("org.objectweb.asm.tree.InsnNode");
var JumpInsnNode = Java.type("org.objectweb.asm.tree.JumpInsnNode");
var LabelNode = Java.type("org.objectweb.asm.tree.LabelNode");
var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
var ASMAPI = Java.type("net.minecraftforge.coremod.api.ASMAPI");

function initializeCoreMod() {
    return {
        "smooth-scrolling-everywhere": {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.network.NetworkManager'
            },
            'transformer': function (classNode) {
                var processPacket = ASMAPI.mapMethod("func_197664_a");
                for (i in classNode.methods) {
                    var method = classNode.methods[i];
                    if (method.name === processPacket) {
                        var instructions = method.instructions;
                        var insnArray = instructions.toArray();
                        for (j in insnArray) {
                            var instruction = insnArray[j];
                            if (instruction instanceof LabelNode) {
                                instructions.insertBefore(instruction, new LabelNode());
                                instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
                                instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC, "me/shedaniel/lightoverlay/LightOverlay", "processPacket", "(Lnet/minecraft/network/IPacket;)V", false));
                                break;
                            }
                        }
                        break;
                    }
                }
                return classNode;
            }
        }
    }
}