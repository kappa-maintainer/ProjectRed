package mrtjp.projectred.compatibility

import mrtjp.projectred.core.{Configurator, IProxy}
import sun.security.krb5.Config

class CompatibilityProxy_server extends IProxy
:
    def preinit(): Unit =
        Services.servicesLoad()
        Services.doPreInit()

    def init(): Unit =
        Services.doInit()

    def postinit(): Unit =
        Services.doPostInit()

class CompatibilityProxy_client extends CompatibilityProxy_server

object CompatibilityProxy extends CompatibilityProxy_client