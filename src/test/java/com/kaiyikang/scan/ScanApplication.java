package com.kaiyikang.scan;

import com.kaiyikang.imported.LocalDateConfiguration;
import com.kaiyikang.imported.ZonedDateConfiguration;
import com.kaiyikang.winter.annotation.ComponentScan;
import com.kaiyikang.winter.annotation.Import;

@ComponentScan
@Import({ LocalDateConfiguration.class, ZonedDateConfiguration.class })
public class ScanApplication {

}
