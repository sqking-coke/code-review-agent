package com.codereview;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

/**
 * 代码智能审查Agent - 启动入口
 */
@SpringBootApplication
public class CodeReviewAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewAgentApplication.class, args);
    }
}
