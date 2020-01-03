package no.nav.helse.sparkel.egenansatt

import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import org.apache.cxf.feature.Feature
import org.apache.cxf.interceptor.Interceptor
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.message.Message
import javax.xml.namespace.QName

object EgenAnsattFactory {

    private val ServiceClass = EgenAnsattV1::class.java
    private val Wsdl = "wsdl/no/nav/tjeneste/pip/EgenAnsatt/v1/Binding.wsdl"
    private val Namespace = "http://nav.no/tjeneste/pip/egenAnsatt/v1/"
    private val ServiceName = QName(Namespace, "EgenAnsatt_v1")
    private val EndpointName = QName(Namespace, "EgenAnsatt_v1Port")

    fun create(endpointUrl: String, features: List<Feature> = emptyList(), outInterceptors: List<Interceptor<Message>> = emptyList()) =
            JaxWsProxyFactoryBean().apply {
                address = endpointUrl
                wsdlURL = Wsdl
                serviceName = ServiceName
                endpointName = EndpointName
                serviceClass = ServiceClass
                this.features.addAll(features)
                this.outInterceptors.addAll(outInterceptors)
            }.create(ServiceClass)
}
