package com.ae2webmanagement.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文启动冒烟测试：数据源改用内存 H2，避免与正在运行的后端
 * （文件库 {@code data/ae2web.mv.db} 被 H2 独占锁定）或开发数据互相干扰。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:backend;DB_CLOSE_DELAY=-1")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}