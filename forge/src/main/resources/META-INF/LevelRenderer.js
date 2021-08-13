var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
var ASMAPI = Java.type("net.minecraftforge.coremod.api.ASMAPI");

function transformMethod(method) {
    var instructions = method.instructions;
    instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/shedaniel/lightoverlay/forge/mixin/MixinLevelRenderer", "setupTerrain", "(Lnet/minecraft/client/renderer/culling/Frustum;)V", false));
    instructions.insert(new VarInsnNode(Opcodes.ALOAD, 2));
}

function initializeCoreMod() {
    return {
        "lightoverlay": {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.client.renderer.LevelRenderer'
            },
            'transformer': function (classNode) {
                var setupRender = ASMAPI.mapMethod("m_109695_");
                for (i in classNode.methods) {
                    var method = classNode.methods[i];
                    if (method.name === setupRender) {
                        transformMethod(method)
                        break;
                    }
                }
                return classNode;
            }
        }
    }
}