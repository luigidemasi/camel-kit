# Expected Migration Output

This directory describes the expected output when migrating the BizTalk order processing example to Apache Camel.

## Generated Files

### 1. Camel YAML Routes

**order-process-orchestration.camel.yaml**

```yaml
- route:
    id: OrderProcessOrchestration
    from:
      uri: "file:{{orders.input.directory}}"
      parameters:
        fileName: "*.xml"
        delay: "{{orders.polling.interval}}"
      steps:
        - unmarshal:
            jaxb:
              contextPath: "com.myapp.schemas"
        - to: "validator:OrderSchema.xsd"
        - choice:
            when:
              - expression:
                  simple: "${body.orderType} == 'Priority'"
                steps:
                  - to: "xslt:OrderToInvoice.xslt"
                  - to: "sql:insert-invoice"
            otherwise:
              steps:
                - log: "Standard order - no invoice generated"
```

### 2. Application Properties

**application.properties**

```properties
# File adapter configuration (from PortBindings.xml)
orders.input.directory=/opt/camel/orders/in
orders.polling.interval=60000

# SQL adapter configuration (from PortBindings.xml)
camel.component.sql.data-source=#bean:invoiceDataSource

# DataSource configuration (from binding Address)
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=InvoiceDB
spring.datasource.username=sa
spring.datasource.password=${SQL_PASSWORD}
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# SQL query (from orchestration Send port)
sql.insert-invoice=INSERT INTO Invoices (CustomerName, InvoiceDate, TotalAmount) VALUES (:#customerName, :#invoiceDate, :#totalAmount)
```

### 3. XSLT Transformation (from BTM)

**OrderToInvoice.xslt**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:order="http://MyApp.Schemas.OrderSchema"
                xmlns:invoice="http://MyApp.Schemas.InvoiceSchema">
  
  <xsl:template match="/order:OrderSchema">
    <invoice:InvoiceSchema>
      <!-- String Concatenate functoid: FirstName + " - " + LastName -->
      <invoice:CustomerName>
        <xsl:value-of select="concat(order:FirstName, ' - ', order:LastName)"/>
      </invoice:CustomerName>
      
      <!-- Scripting functoid: C# DateTime.Parse -> yyyy-MM-dd -->
      <invoice:InvoiceDate>
        <xsl:value-of select="format-date(xs:date(order:OrderDate), '[Y0001]-[M01]-[D01]')"/>
      </invoice:InvoiceDate>
      
      <!-- Math functoid: Quantity * UnitPrice -->
      <invoice:TotalAmount>
        <xsl:value-of select="order:Quantity * order:UnitPrice"/>
      </invoice:TotalAmount>
    </invoice:InvoiceSchema>
  </xsl:template>
  
</xsl:stylesheet>
```

### 4. Maven/Gradle Dependencies

**pom.xml additions**

```xml
<dependencies>
  <!-- Camel Core + Spring Boot -->
  <dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-spring-boot-starter</artifactId>
  </dependency>
  
  <!-- File component (from FILE adapter) -->
  <dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-file-starter</artifactId>
  </dependency>
  
  <!-- SQL component (from SQL adapter) -->
  <dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-sql-starter</artifactId>
  </dependency>
  
  <!-- XSLT component (from Transform shape) -->
  <dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-xslt-starter</artifactId>
  </dependency>
  
  <!-- Bean Validation (from XMLReceive.btp) -->
  <dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-bean-validator-starter</artifactId>
  </dependency>
  
  <!-- SQL Server JDBC driver -->
  <dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
  </dependency>
</dependencies>
```

### 5. Migration Notes

**migration-notes.md**

```markdown
# BizTalk to Camel Migration Notes

## Successfully Migrated

- Orchestration flow structure (Receive -> Decide -> Construct/Transform -> Send)
- FILE adapter polling configuration
- SQL adapter endpoint configuration
- BizTalk Map functoids converted to XSLT 2.0
- XML validation pipeline stage

## Manual Review Required

1. **SQL INSERT statement** - Verify column mappings match target database schema
2. **Date format conversion** - BizTalk C# DateTime.Parse vs XSLT format-date() may differ for edge cases
3. **Error handling** - BizTalk compensation/exception handlers not present in this example
4. **Transaction management** - BizTalk orchestration transactions need explicit Camel error handler configuration

## BizTalk Features Not Present in Example

- Long-running orchestration (persistence/dehydration)
- Correlation sets
- Parallel convoy patterns
- BAM tracking points
- Business rules engine integration

## Testing Recommendations

1. Unit test the XSLT transformation with sample order XML files
2. Integration test with a test SQL database
3. Compare output invoice XML with BizTalk orchestration output
4. Validate performance under expected file polling load
```

## Mapping Summary

| BizTalk Artifact | Camel Equivalent | Notes |
|-----------------|------------------|-------|
| Orchestration Service | Camel Route | One route per orchestration |
| Receive Shape (activate) | `from` endpoint | FILE component with polling |
| Decide Shape | `choice` / `when` | Simple expression language |
| Construct + Transform | `to` XSLT endpoint | BTM map -> XSLT conversion |
| Send Shape | `to` endpoint | SQL component with named query |
| FILE Adapter | `camel-file` | Polling interval from bindings |
| SQL Adapter | `camel-sql` | DataSource from binding address |
| XMLReceive Pipeline | `unmarshal` + `validator` | JAXB + Bean Validator |
| String Concatenate Functoid | `concat()` XSLT function | Direct translation |
| Scripting Functoid (C#) | XSLT 2.0 function | Manual conversion required |
| Math Functoid | XPath arithmetic | Direct translation |

## Component Dependencies

The migration requires these Camel components (detected from BizTalk adapter types):

- `camel-file` - FILE adapter replacement
- `camel-sql` - SQL adapter replacement  
- `camel-xslt` - BizTalk map transformation
- `camel-bean-validator` - Pipeline validation stage

## Configuration Externalization

All environment-specific values (file paths, database URLs, credentials) are externalized to `application.properties` with placeholders for deployment-time configuration.
