package io.mongock.runner.springboot.mongockscanpackage;

import io.mongock.runner.springboot.MongockScanPackage;
import org.springframework.context.annotation.Configuration;

@MongockScanPackage(value = {
  "com.example.packageone.migration",
  "com.example.packagetwo.migration"
})
@Configuration
public class ModuleConfigMongockScanPackage {
}
