package mrtjp.projectred.transportation

import mrtjp.projectred.api.{ISpecialLinkState, ITransportationAPI}

class APIImpl_Transportation extends ITransportationAPI
{
    override def registerSpecialLinkState(link: ISpecialLinkState): Unit =
    {
        LSPathFinder.register(link)
    }
}
