/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core;

/**
 * 用于管理别名的通用接口
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 */
public interface AliasRegistry {

	/**
	 * 注册别名
	 *
	 * @param name  实际名称
	 * @param alias 别名
	 * @throws IllegalStateException 别名已被使用且不可覆盖
	 */
	void registerAlias(String name, String alias);

	/**
	 * 删除指定别名要删除的别名
	 *
	 * @throws IllegalStateException 别名不存在
	 */
	void removeAlias(String alias);

	/**
	 * 检查给定名称是否已定义别名
	 *
	 * @param name 待检查的实际名称
	 * @return 是否已定义别名
	 */
	boolean isAlias(String name);

	/**
	 * 获取给定名称的别名
	 *
	 * @param name 实际名称
	 * @return 别名数字，不存在则返回空数组
	 */
	String[] getAliases(String name);

}
