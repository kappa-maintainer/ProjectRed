package mrtjp.projectred.core

trait IProxy
{
    def preinit(): Unit

    def init(): Unit

    def postinit(): Unit
}
