package de.sekmi.li2b2.services.impl;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Singleton;

//import javax.inject.Singleton;

import de.sekmi.li2b2.api.pm.Project;
import de.sekmi.li2b2.api.pm.ProjectManager;
import de.sekmi.li2b2.api.pm.User;

@Singleton
public class ProjectManagerImpl implements ProjectManager {

	private List<UserImpl> users;
	private List<ProjectImpl> projects;

	public ProjectManagerImpl(){
		this.users = new ArrayList<>();
		projects = new ArrayList<>(3);
	}
	@Override
	public User getUserById(String userId, String domain) {
		for( UserImpl user : users ){
			if( user.getDomain().equals(domain) && user.getName().equals(userId) ){
				return user;
			}
		}
		return null;
	}

	@Override
	public Project getProjectById(String projectId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public User addUser(String userId, String domain) {
		UserImpl user = new UserImpl(this, userId,domain);
		users.add(user);
		return user;
	}
	@Override
	public Project addProject(String id, String name) {
		ProjectImpl p = new ProjectImpl(id, name);
		projects.add(p);
		return p;
	}
	public Iterable<Project> getUserProjects(User user){
		List<Project> up = new LinkedList<>();
		for( Project p : projects ){
			if( !p.getUserRoles(user).isEmpty() ){
				up.add(p);
			}
		}
		return up;
	}

}
