package edu.cit.sanico.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "AuthRequest")
record XmlAuthRequest(
    @JacksonXmlProperty(localName = "ClientId") String clientId,
    @JacksonXmlProperty(localName = "ApiKey") String apiKey
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "AuthResponse")
record XmlAuthResponse(
    @JacksonXmlProperty(localName = "SessionToken") String sessionToken,
    @JacksonXmlProperty(localName = "IssuedAt") String issuedAt
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "Item")
record XmlCatalogItem(
    @JacksonXmlProperty(localName = "SupplierSku") String supplierSku,
    @JacksonXmlProperty(localName = "Description") String description,
    @JacksonXmlProperty(localName = "PackSize") int packSize,
    @JacksonXmlProperty(localName = "UnitCost") String unitCost
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "Catalog")
record XmlCatalog(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Item")
    List<XmlCatalogItem> items
) {}

@JacksonXmlRootElement(localName = "PurchaseOrder")
record XmlPurchaseOrder(
    @JacksonXmlProperty(localName = "SupplierSku") String supplierSku,
    @JacksonXmlProperty(localName = "Qty") int qty,
    @JacksonXmlProperty(localName = "BuyerRef") String buyerRef
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "PurchaseOrderAck")
record XmlPurchaseOrderAck(
    @JacksonXmlProperty(localName = "PoNumber") String poNumber,
    @JacksonXmlProperty(localName = "StatusCode") String statusCode,
    @JacksonXmlProperty(localName = "SupplierSku") String supplierSku,
    @JacksonXmlProperty(localName = "Qty") int qty,
    @JacksonXmlProperty(localName = "Uom") String uom,
    @JacksonXmlProperty(localName = "BuyerRef") String buyerRef,
    @JacksonXmlProperty(localName = "CreatedAt") String createdAt
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "PurchaseOrderStatus")
record XmlPurchaseOrderStatus(
    @JacksonXmlProperty(localName = "PoNumber") String poNumber,
    @JacksonXmlProperty(localName = "StatusCode") String statusCode,
    @JacksonXmlProperty(localName = "SupplierSku") String supplierSku,
    @JacksonXmlProperty(localName = "Qty") int qty,
    @JacksonXmlProperty(localName = "Uom") String uom,
    @JacksonXmlProperty(localName = "BuyerRef") String buyerRef,
    @JacksonXmlProperty(localName = "CreatedAt") String createdAt,
    @JacksonXmlProperty(localName = "CheckedAt") String checkedAt
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "LSError")
record XmlError(
    @JacksonXmlProperty(localName = "Code") String code,
    @JacksonXmlProperty(localName = "Message") String message
) {}
