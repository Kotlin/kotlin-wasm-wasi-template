package deno

import java.io.File

fun getDenoExecutableText(wasmFileName: String): String = """
import Context from "https://deno.land/std@0.201.0/wasi/snapshot_preview1.ts";

const context = new Context({
  args: Deno.args,
  env: Deno.env.toObject(),
});

const binary = await Deno.readFile("./$wasmFileName");
const module = await WebAssembly.compile(binary);
const wasmInstance = await WebAssembly.instantiate(module, {
  "wasi_snapshot_preview1": context.exports,
});

context.initialize(wasmInstance);
wasmInstance.exports.startUnitTests?.();
"""

fun prepareFile(isolationDir: File, denoFileName: String, wasmFile: File): File {
    val denoMjs = isolationDir.resolve(denoFileName)
    denoMjs.writeText(
        getDenoExecutableText(
            wasmFile.relativeTo(isolationDir).invariantSeparatorsPath
        )
    )

    return denoMjs
}

fun denoArgs(startFile: File): List<String> =
    buildList {
        add("run")
        add("--allow-read")
        add("--allow-env")
        add(startFile.normalize().absolutePath)
    }
