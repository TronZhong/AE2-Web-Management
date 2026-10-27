/**
 * 应用服务层：编排用例逻辑（登录、授权、库存查询、合成命令等）。
 *
 * <p>本层负责事务边界与流程编排，通过 {@code repository} 访问数据、
 * 通过 {@code client} 访问模组 API，不直接暴露 HTTP。</p>
 */
package com.ae2webmanagement.backend.service;