package io.github.wu4ngg.simplejobqueue.models

data class Endpoint(val id: String, val url: String, val secret: Secret) {
}

@JvmInline
value class Secret(val value: String) {
    override fun toString(): String {
        return "Secret(***)"
    }
}