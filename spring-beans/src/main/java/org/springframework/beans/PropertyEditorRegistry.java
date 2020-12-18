/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.beans;

import java.beans.PropertyEditor;

import org.springframework.lang.Nullable;

/**
 * 管理PropertyEditor的中心接口，负责注册、查找对应的PropertyEditor
 *
 * @author Juergen Hoeller
 * @since 1.2.6
 * @see java.beans.PropertyEditor
 * @see PropertyEditorRegistrar
 * @see BeanWrapper
 * @see org.springframework.validation.DataBinder
 */
public interface PropertyEditorRegistry {

	/**
	 * 将给定类型的所有属性注册到给定的PropertyEditor
	 * @param requiredType 属性的类型
	 * @param propertyEditor 给定的PropertyEditor
	 */
	void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor);

	/**
	 * 将给的属性类型以及属性名字的属性注册到给定的PropertyEditor
	 *
	 * @param requiredType 属性的类型
	 * @param propertyPath 属性名路径
	 * @param propertyEditor 给定的PropertyEditor
	 */
	void registerCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath, PropertyEditor propertyEditor);

	/**
	 * 根据指定的类型以及属性的名字，查询其对应的ProeprtyEditor，属性的名字可以为null
	 *
	 * @param requiredType 属性的类型（如果指定了属性路径，则可以为null ，该属性会用于一致性检查，建议必传）
	 * @param propertyPath 属性的路径（名称或嵌套路径）；如果为给定类型的所有属性寻找编辑器，则为null
	 * @return  PropertyEditor
	 */
	@Nullable
	PropertyEditor findCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath);

}
