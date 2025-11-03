package com.kaiyikang.winter.jdbc.tx;

import com.kaiyikang.winter.annotation.Transactional;
import com.kaiyikang.winter.aop.AnnotationProxyBeanPostProcessor;

public class TransactionBeanPostProcessor extends AnnotationProxyBeanPostProcessor<Transactional> {

}
