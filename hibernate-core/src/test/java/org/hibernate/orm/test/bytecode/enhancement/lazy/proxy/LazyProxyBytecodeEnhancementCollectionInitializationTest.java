/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.bytecode.enhancement.lazy.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.boot.internal.SessionFactoryBuilderImpl;
import org.hibernate.boot.internal.SessionFactoryOptionsBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.SessionFactoryBuilderService;
import org.hibernate.engine.spi.SessionImplementor;

import org.hibernate.testing.bytecode.enhancement.BytecodeEnhancerRunner;
import org.hibernate.testing.bytecode.enhancement.EnhancementOptions;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Test that collections are not initialized immediately
 * when creating a proxy with {@code session.getReference}
 * and bytecode enhancement is enabled with {@code enableCollectionInDefaultFetchGroup = true}.
 * <p>
 * See <a href="https://github.com/hibernate/hibernate-orm/pull/5252#issuecomment-1236635727">this comment</a>.
 */
@RunWith(BytecodeEnhancerRunner.class)
@EnhancementOptions(lazyLoading = true)
public class LazyProxyBytecodeEnhancementCollectionInitializationTest
		extends BaseCoreFunctionalTestCase {


	@Override
	public Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] { Parent.class, Child.class };
	}

	@Override
	protected void prepareBasicRegistryBuilder(StandardServiceRegistryBuilder serviceRegistryBuilder) {
		serviceRegistryBuilder.addService(
				SessionFactoryBuilderService.class,
				(SessionFactoryBuilderService) (metadata, bootstrapContext) -> {
					SessionFactoryOptionsBuilder optionsBuilder = new SessionFactoryOptionsBuilder(
							metadata.getMetadataBuildingOptions().getServiceRegistry(),
							bootstrapContext
					);
					// We want to test this configuration exactly
					optionsBuilder.enableCollectionInDefaultFetchGroup( true );
					return new SessionFactoryBuilderImpl( metadata, optionsBuilder );
				}
		);
	}

	@Before
	public void prepare() {
		inTransaction( s -> {
			Parent parent = new Parent();
			parent.setId( 1 );
			for ( int i = 0; i < 2; i++ ) {
				Child child = new Child();
				child.setId( i );
				s.persist( child );
				child.setParent( parent );
				parent.getChildren().add( child );
			}
			s.persist( parent );
		} );
	}

	@Test
	public void collectionInitializationOnLazyProxy() {
		inTransaction( s -> {
			Parent parent = s.getReference( Parent.class, 1 );
			assertThat( Hibernate.isPropertyInitialized( parent, "children") ).isFalse();
			assertThat( s.unwrap( SessionImplementor.class ).getPersistenceContext().getCollectionEntries() )
					.isEmpty();

			// Accessing a collection property on a lazy proxy initializes the property and instantiates the collection,
			// but does not initialize the collection.
			List<Child> children = parent.getChildren();
			assertThat( Hibernate.isPropertyInitialized( parent, "children") ).isTrue();
			assertThat( s.unwrap( SessionImplementor.class ).getPersistenceContext().getCollectionEntries() )
					.hasSize( 1 );
			assertThat( Hibernate.isInitialized( children ) ).isFalse();

			children.size();
			assertThat( Hibernate.isInitialized( children ) ).isTrue();
		} );
	}

	@Entity(name = "Parent")
	@Table
	private static class Parent {

		@Id
		Integer id;

		@OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
		List<Child> children = new ArrayList<>();

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public List<Child> getChildren() {
			return children;
		}

		public void setChildren(List<Child> children) {
			this.children = children;
		}
	}

	@Entity(name = "Child")
	@Table
	private static class Child {

		@Id
		Integer id;

		@ManyToOne(fetch = FetchType.LAZY)
		Parent parent;

		Child() {
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Parent getParent() {
			return parent;
		}

		public void setParent(Parent owner) {
			this.parent = owner;
		}
	}

}
