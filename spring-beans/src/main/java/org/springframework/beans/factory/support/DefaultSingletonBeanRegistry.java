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

package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/** 最大抑制异常数 */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


	/** 单例对象一级缓存，存放完全实例化且属性赋值完成的 Bean ，可以直接使用 */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/** 三级缓存，存放实例化完成的 Bean 工厂*/
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/** 单例对象二级缓存，存放早期 Bean 的引用，尚未装配属性的 Bean */
	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	/** 已注册的单例bean集合，按注册顺序储存BeanName */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/** 当前正在创建的bean的名称,bean 在创建的过程中都会存储在此，创建完成移出 */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** 无需进行创建检查的bean名称 */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** 抑制的异常的集合，可用于关联相关原因 */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/** 当前singletons销毁中 */
	private boolean singletonsCurrentlyInDestruction = false;

	/** 非单例Bean缓存（一次性Bean） */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/** 子bean映射，储存bean中包含的其他bean;<beanName, Set<childBeanName>> */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** 从属bean缓存， bean名称到依赖于当前bean的所有bean的名称集合（bean销毁时必须同时销毁依赖于当前bean的所有bean） */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** 依赖Bean储存， bean名称到当前bean依赖的所有bean的名称集合 （当前bean依赖的所有bean在销毁时都需要同时销毁当前bean）*/
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	/**
	 * 注册已初始化完成的单例Bean到缓存，如果已存在则抛出异常
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 添加bean对象到一级缓存
	 * 将创建好的Bean添加到一级缓存和已注册的单例Bean集合中，删除二级和三级缓存bean
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, singletonObject);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * 添加单例工厂来构建指定bean（可用于解决循环依赖问题）
	 * 如果bean不在一级缓存中，在添加三级缓存（单例工厂），并删除二级缓存（如果有）
	 * @param beanName bean名
	 * @param singletonFactory 单例对象的工厂
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}

	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * 逐级获取单例对象，如果一级或二级缓存存在该单例直接获取，
	 * 如果不存在则通过三级缓存工厂进行创建并添加至二级缓存，同时删除该工厂
	 * @param beanName 要获取的Bean名
	 * @param allowEarlyReference 是否添加至二级缓存
	 * @return 单例对象。未获取到返回null
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// 先从一级缓存中获取，获取到直接返回
		Object singletonObject = this.singletonObjects.get(beanName);
		// 如果未获取到且单例正在创建，则从二级缓存中获取，获取到直接返回
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// 从二级缓存中获取
			singletonObject = this.earlySingletonObjects.get(beanName);
			//二级缓存未获取到且允许添加到二级缓存
			// 则从三级缓存使用工厂获取bean，添加到二级缓存，并删除三级缓存
			// 即将bean从三级缓存移至二级缓存
			if (singletonObject == null && allowEarlyReference) {
				synchronized (this.singletonObjects) {
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								singletonObject = singletonFactory.getObject();
								this.earlySingletonObjects.put(beanName, singletonObject);
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
		return singletonObject;
	}

	/**
	 * 获取单例对象，如果不存在则使用工厂获取新单例
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		//beanName不为null
		Assert.notNull(beanName, "Bean name must not be null");
		synchronized (this.singletonObjects) {
			//从一级缓存获取，获取到直接返回
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				//销毁中的Singleton不允许创建bean
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				//创建单例之前的钩子（将单例注册为创建中状态）
				beforeSingletonCreation(beanName);
				//单例创建结果
				boolean newSingleton = false;
				//异常记录集合是否为null
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					//获取此工厂管理的对象的实例，获取过程中如果出现了不影响流程（被抑制）的异常，
					// 直接调用onSuppressedException方法，存入this.suppressedExceptions
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				} catch (IllegalStateException ex) {
					// 如果使用工厂获取单例时抛出IllegalStateException，
					// 先确认一级缓存中是否已隐式（异步）创建了单例，如果有直接返回，确认没有才抛出该异常
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						//遍历singletonFactory.getObject()中出现的异常，添加至BeanCreationException的相关异常中
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					//创建单例完成之后的钩子（取消Bean创建中状态）
					afterSingletonCreation(beanName);
				}
				// 创建成功，添加至一级缓存
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}

	/**
	 * 注册一个在创建单例bean实例期间被抑制（未抛出）的异常，即将异常添加至this.suppressedExceptions
	 *
	 * @param ex 要注册的异常
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * 清除缓存中的单例bean，同时清除一级二级三级缓存和已注册的单例bean集合
	 *
	 * @param beanName 要清除的bean名
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	/**
	 * 判断一级缓存中是否包含该bean
	 */
	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	/**
	 * 获取所有已注册的单例beanName
	 */
	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	/**
	 * 获取已注册的单例bean数量
	 */
	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	/**
	 * 设置创建检查，如果bean为创建中则移除忽略检查，需要对bean进行创建检查
	 * @param beanName bean名
	 * @param inCreation 是否为创建中
	 */
	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		// beanName不为null
		Assert.notNull(beanName, "Bean name must not be null");
		// 如果bean为创建中则移除忽略检查，需要对bean进行创建检查
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	/**
	 * 是否需要创建检查，不在无需进行创建检查的bean名称集合中且处于创建中的bean
	 * @param beanName
	 * @return
	 */
	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	/**
	 * 单例bean创建中
	 */
	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * 单例bean是否为创建中
	 * @param beanName bean名
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 创建单例之前的钩子，将单例注册为当前正在创建中（添加到当前正在创建的Bean名称列表中）
	 * @param beanName 要创建的单例的名称
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * 创建单例之后的钩子，取消单例正在创建中的状态（从当前正在创建的Bean名称列表中移除）
	 * @param beanName 要创建的单例的名称
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * 注册非单例bean至disposableBeans
	 * @param beanName bean名
	 * @param bean 非单例bean
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * 注册两个bean的包含关系
	 *
	 * @param containedBeanName 被包含的bean(内部)
	 * @param containingBeanName 包含的bean(外部)
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			//获取当前（外部）bean中已包含的子bean
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			//将需要绑定的子bean添加到已包含的beans中，添加失败说明当前包含关系已存在，无需重新注册则直接返回
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 在给定的bean被销毁之前，为给定的bean注册一个要被销毁的依赖bean，即在销毁bean之前需要先销毁dependentBean
	 * 一般bean（内部）包含于dependentBean（外部）,bean可能会有业务被dependentBean引用，所有在销毁bean之前需要先销毁dependentBean
	 * @param beanName bean名（内部）
	 * @param dependentBeanName 依赖bean的名称（外部）
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		// 获取bean的原始名称（beanName可能会是别名）
		String canonicalName = canonicalName(beanName);

		// 绑定bean的从属关系
		synchronized (this.dependentBeanMap) {
			// 获取bean所从属的所有dependentBean，即bean在哪些bean内被引用
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}
		// 绑定bean的依赖关系
		synchronized (this.dependenciesForBeanMap) {
			// 获取所有dependentBean依赖的bean，这些依赖的bean在销毁前都需要先销毁dependentBean，因为在dependentBean内有这些bean的引用
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * 确定bean是否与dependentBean有依赖或传递依赖关系（bean是否在被dependentBean直接或间接引用）
	 * @param beanName 要检查的bean的名称
	 * @param dependentBeanName 依赖bean的名称
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	/**
	 * 检查bean是否与dependentBean有依赖或传递依赖关系（bean是否在被dependentBean直接或间接引用）
	 * @param beanName 要检查的bean的名称
	 * @param dependentBeanName 依赖bean的名称
	 * @param alreadySeen 已检查过的bean名集合
	 * @return
	 */
	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		// 已检查过的直接返回结果（false）
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		// 获取bean的原始名称（beanName可能会是别名）
		String canonicalName = canonicalName(beanName);
		// 获取所有依赖于bean的dependentBean,即bean销毁之前必须先销毁的dependentBean
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		// 如果没有直接返回结果(false)
		if (dependentBeans == null) {
			return false;
		}
		// 如果当前dependentBean在依赖于bean的dependentBean集合中，则直接返回结果（true）
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		// 遍历依赖于bean的所有dependentBean，检查是否有传递依赖
		// 检查所有依赖于dependentBean（依赖于bean的所有dependentBean）的dependentDependentBean,是否有当前dependentBeanName
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 检查当前bean是否存在依赖bean，即判断当前bean是否有被引用
	 * @param beanName 要检查的bean的名称
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * 获取依赖于指定bean的所有bean的名称（bean销毁时必须同时销毁依赖于他的bean）。
	 * @param beanName bean名
	 * @return 依赖bean名称的数组，如果没有则为空数组
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * 返回指定bean依赖的所有bean的名称（被依赖的bean销毁需要先销毁依赖bean）
	 *
	 * @param beanName bean名
	 * @return bean所依赖的bean的名称数组，如果没有，则为空数组
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	/**
	 * 销毁该工厂中的所有单例bean，包括已注册为一次性的非单例bean。 在工厂关闭时被调用。
	 * 销毁期间发生的任何异常都应捕获并记录，而不是传播给此方法的调用方
	 */
	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		// 单例状态更新为销毁中
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		clearSingletonCache();
	}

	/**
	 * 清除所有缓存的单例实例。
	 *
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * 销毁给定的bean。如果找到相应的一次性bean实例，则委托给{@code destroyBean} *。
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// 将当前bean从缓存中清除
		removeSingleton(beanName);

		// 如果当前bean存在一次性bean也同时删除
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		// 销毁一次性bean
		destroyBean(beanName, disposableBean);
	}

	/**
	 * 销毁给定的bean。 必须先销毁依赖于给定bean的bean，然后再销毁bean
	 * @param beanName bean名
	 * @param bean 要销毁的bean实例
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// 销毁依赖于当前bean的bean
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// 清除当前bean的被依赖记录，并返回依赖于当前bean的所有bean名称
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			// 遍历销毁所有依赖于当前bean的bean
			for (String dependentBeanName : dependencies) {
				destroySingleton(dependentBeanName);
			}
		}

		// 销毁指定的非单例（一次性）bean
		if (bean != null) {
			try {
				bean.destroy();
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// 销毁当前bean的子bean
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// 清除当前bean的子bean记录，并返回当前bean的所有子bean名称
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			// 遍历销毁当前bean的所有子bean
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// 从依赖于其他bean的依赖项中删除当前销毁的bean
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// 清除当前bean的依赖记录（bean名称到当前bean依赖的所有bean的名称集合）
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * 获取当前使用的单例互斥体（一级缓存）供子类或外部使用，避免子类在扩展时自己创建，出现循环依赖问题造成死锁
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
