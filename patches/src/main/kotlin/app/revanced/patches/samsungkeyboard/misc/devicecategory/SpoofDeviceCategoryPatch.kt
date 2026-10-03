package app.revanced.patches.samsungkeyboard.misc.devicecategory

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.findInstructionIndicesReversed
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstStringInstruction
import app.revanced.patches.samsungkeyboard.misc.nononeui.enableNonOneUiPatch
import app.revanced.patches.samsungkeyboard.shared.Constants.COMPATIBILITY_SAMSUNG_KEYBOARD
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val TABLET_FEATURE = "com.samsung.feature.device_category_tablet"
private const val PACKAGE_MANAGER_TYPE = "Landroid/content/pm/PackageManager;"
private const val EXTENSION_PACKAGE = "Lapp/revanced/extension/samsungkeyboard/"
private const val DEVICE_CATEGORY_COMPAT_TYPE = "${EXTENSION_PACKAGE}DeviceCategoryCompat;"

private val hasSystemFeatureCompat = ImmutableMethodReference(
    DEVICE_CATEGORY_COMPAT_TYPE,
    "hasSystemFeature",
    listOf(PACKAGE_MANAGER_TYPE, "Ljava/lang/String;"),
    "Z",
)

@Suppress("unused")
val spoofDeviceCategoryPatch = bytecodePatch(
    name = "Spoof device category",
    description = "Lets Samsung Keyboard use its tablet UX on non-Samsung tablets, " +
        "for example the split keyboard in portrait. " +
        "The keyboard only checks a Samsung specific system feature, which this patch answers instead.",
    use = false,
) {
    compatibleWith(COMPATIBILITY_SAMSUNG_KEYBOARD)
    dependsOn(enableNonOneUiPatch)

    val deviceCategory by stringOption(
        key = "deviceCategory",
        default = "Auto",
        values = mapOf(
            "Auto (tablet when the screen is at least 600dp wide)" to "Auto",
            "Tablet" to "Tablet",
            "Phone" to "Phone",
        ),
        title = "Device category",
        description = "Which device category Samsung Keyboard should assume.",
        required = true,
    )

    execute {
        val replaced = redirectTabletFeatureChecks()
        if (replaced == 0) throw PatchException("Could not find any $TABLET_FEATURE check.")

        setConfiguredCategory(deviceCategory!!)
    }
}

private fun BytecodePatchContext.redirectTabletFeatureChecks(): Int {
    var replaced = 0
    classDefForEach classLoop@{ classDef ->
        if (classDef.type.startsWith(EXTENSION_PACKAGE)) return@classLoop
        val mutableClass by lazy { mutableClassDefBy(classDef) }

        classDef.methods.forEach methodLoop@{ method ->
            if (method.implementation == null) return@methodLoop
            if (method.indexOfFirstStringInstruction(TABLET_FEATURE) < 0) return@methodLoop

            val mutableMethod = mutableClass.findMutableMethodOf(method)
            method.findInstructionIndicesReversed { isHasSystemFeatureCall() }.forEach { index ->
                mutableMethod.redirectInvoke(index)
                replaced++
            }
        }
    }
    return replaced
}

private fun Instruction.isHasSystemFeatureCall(): Boolean {
    if (opcode != Opcode.INVOKE_VIRTUAL && opcode != Opcode.INVOKE_VIRTUAL_RANGE &&
        opcode != Opcode.INVOKE_INTERFACE && opcode != Opcode.INVOKE_INTERFACE_RANGE
    ) return false
    val reference = getReference<MethodReference>() ?: return false
    return reference.definingClass == PACKAGE_MANAGER_TYPE &&
        reference.name == "hasSystemFeature" &&
        reference.parameterTypes == listOf("Ljava/lang/String;") &&
        reference.returnType == "Z"
}

private fun MutableMethod.redirectInvoke(index: Int) {
    val instruction = getInstruction<Instruction>(index)
    val replacement = when (instruction) {
        is FiveRegisterInstruction -> BuilderInstruction35c(
            Opcode.INVOKE_STATIC,
            instruction.registerCount,
            instruction.registerC,
            instruction.registerD,
            instruction.registerE,
            instruction.registerF,
            instruction.registerG,
            hasSystemFeatureCompat,
        )
        is RegisterRangeInstruction -> BuilderInstruction3rc(
            Opcode.INVOKE_STATIC_RANGE,
            instruction.startRegister,
            instruction.registerCount,
            hasSystemFeatureCompat,
        )
        else -> throw PatchException("Unsupported invocation instruction at $index.")
    }
    replaceInstruction(index, replacement)
}

private fun BytecodePatchContext.setConfiguredCategory(category: String) {
    val method = mutableClassDefBy(classDefBy(DEVICE_CATEGORY_COMPAT_TYPE)).methods.first {
        it.name == "configuredCategory" && it.parameterTypes.isEmpty()
    }
    method.addInstructions(
        0,
        """
            const-string v0, "$category"
            return-object v0
        """.trimIndent(),
    )
}
