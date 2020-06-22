var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var LabelNode = Java.type("org.objectweb.asm.tree.LabelNode");
var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
var ASMAPI = Java.type("net.minecraftforge.coremod.api.ASMAPI");

function initializeCoreMod() {
    return {
        "light-overlay-forge": {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.client.renderer.debug.DebugRenderer'
            },
            'transformer': function (classNode) {
                var render = ASMAPI.mapMethod("func_229019_a_");
                for (i in classNode.methods) {
                    var method = classNode.methods[i];
                    if (method.name === render) {
                        var instructions = method.instructions;
                        var insnArray = instructions.toArray();
                        for (j in insnArray) {
                            var instruction = insnArray[j];
                            if (instruction instanceof LabelNode) {
                                instructions.insertBefore(instruction, new LabelNode());
                                instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
                                instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC, "me/shedaniel/lightoverlay/forge/LightOverlayClient", "renderWorldLast", "()V", false));
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