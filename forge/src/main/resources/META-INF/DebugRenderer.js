var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
var ASMAPI = Java.type("net.minecraftforge.coremod.api.ASMAPI");

function transformMethod(method) {
    var instructions = method.instructions;
    instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/shedaniel/lightoverlay/forge/mixin/MixinDebugRenderer", "render", "(Lcom/mojang/blaze3d/vertex/PoseStack;)V", false));
    instructions.insert(new VarInsnNode(Opcodes.ALOAD, 1));
}

function initializeCoreMod() {
    return {
        "lightoverlay": {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.client.renderer.debug.DebugRenderer'
            },
            'transformer': function (classNode) {
                var render = ASMAPI.mapMethod("m_113457_");
                for (i in classNode.methods) {
                    var method = classNode.methods[i];
                    if (method.name === render) {
                        transformMethod(method)
                        break;
                    }
                }
                return classNode;
            }
        }
    }
}